# RF12 — PDF de contrato, pagaré y plan de pagos, con número de operación único

**Estado: cerrado y probado en pantalla completo el 11/10/2026 — tag `rf12-probado`.** El texto
se cumple sin excepción con CC-2026-00001 (los 3 PDF, mismo número, cifras idénticas a la
pantalla). Quedan 6 mejoras menores (sección 10, a-f), que NO se hacen en esta vuelta.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF12 (módulo Formalización):

> El sistema debe generar en PDF el contrato, el pagaré y el plan de pagos del préstamo, con un
> número de operación único.

RF12 se dispara desde el mismo paso que RF11 (fijar la fecha del primer vencimiento): los 3 PDF
se generan en la misma transacción que crea el préstamo, ya con el número asignado.

## 2. Cómo funciona

**a) Dónde está.** Todo vive en `ServicioFormalizacion.java` (no hay un servicio separado para
PDF) y en `src/main/resources/reportes/` (plantillas JasperReports, compiladas a `.jasper` en
`target/classes/reportes/` por el build):

- `contrato.jrxml` / `pagare.jrxml` / `plan_pagos.jrxml` — las 3 plantillas, sin ningún cálculo
  adentro (comentario explícito en `pagare.jrxml:2-3`: "Ningún cálculo acá: todo llega ya
  calculado/formateado desde ServicioFormalizacion", `REGLAS_DESARROLLO.md` §6).
- `ServicioFormalizacion.generarPdfContrato`/`generarPdfPagare`/`generarPdfPlanDePagos` — cada
  uno junta los datos (`construirParametrosReporte`, compartido por los 3) y llama a
  `llenarYExportar`, que carga el `.jasper` del classpath, lo llena con `JasperFillManager` y lo
  exporta a PDF con `JasperExportManager` (JasperReports 6.20.6, `docs/00_especificacion_maestra.md:984`).
- `generarDocumentos` (al formalizar por primera vez) y `regenerarDocumentos` (si cambia la
  fecha) llaman a `generarSiFalta`/`regenerarPdfTipo` → `generarPdfTipo`, que resuelve cuál de
  los 3 generar según el código de tipo (`CONTRATO_GENERADO`/`PAGARE_GENERADO`/`PLAN_PAGOS_GENERADO`)
  y guarda el resultado con `guardarPdfGenerado`.
- `ControladorFormalizacionServlet` — las rutas que disparan esto son `/formalizacion/nueva/{id}/generar`
  (RF11/RF12 juntos) y `/formalizacion/{id}/regenerar`; además `/formalizacion/{id}/generar-pdf`
  (`generarPdfsFaltantes`, idempotente, solo genera lo que falte) y `/formalizacion/{id}/regenerar-pdf`
  (`regenerarSoloPdfs`, siempre re-emite los 3, ver pregunta f).
- `ControladorDocumentosServlet` (`GET /documentos/{idDocumento}`) — sirve el PDF guardado
  (ver pregunta e).

**Cómo se generan los tres PDF** (dentro de `generarDocumentos`, en la misma transacción que
crea el préstamo):
1. Se asigna el número de operación (ver pregunta b).
2. Se crea el préstamo y el plan de pagos (cuotas) en la base.
3. Se llama a `generarSiFalta` para cada uno de los 3 tipos — cada uno arma sus propios
   parámetros/datos (el contrato además construye una lista de "cláusulas", una fila por
   cláusula, para que JasperReports pagine correctamente un documento largo) y guarda el PDF.
4. Todo esto es parte de la misma transacción: si algo falla, no queda ni el préstamo ni ningún
   PDF a medias (rollback completo).

## 3. Auditoría

| Evento | Entidad | Cuándo |
|---|---|---|
| `GENERAR_DOCUMENTOS_FORMALIZACION` | PRESTAMO | Se formalizó (crea el préstamo y los 3 PDF) — detalle con fecha estimada/elegida/final, motivo del corrimiento, cuota más alta y CFT (RF11) |
| `REGENERAR_DOCUMENTOS_FORMALIZACION` | PRESTAMO | Se regeneró el plan con otra fecha (los 3 PDF se re-emiten) o se re-emitieron sin cambiar nada (pregunta f) |
| `GENERAR_PDF_FORMALIZACION` | DOCUMENTO_EXPEDIENTE | Por cada PDF individual generado o reemplazado: "Generó "nombre_archivo.pdf" para CC-...." |

Cada PDF generado es, además, una fila versionada de `documentos_expediente` (ver pregunta e):
nunca se borra ni se sobrescribe, solo se marca `REEMPLAZADO` y se inserta una fila nueva
`VIGENTE` con `version_documento` incrementado.

## 4. Dónde está

(Resumen de la sección 2, para referencia rápida)

- `src/main/resources/reportes/contrato.jrxml`, `pagare.jrxml`, `plan_pagos.jrxml`.
- `servicios/ServicioFormalizacion.java` — `generarPdfContrato`/`generarPdfPagare`/`generarPdfPlanDePagos`,
  `construirParametrosReporte`, `construirClausulasContrato`, `llenarYExportar`,
  `generarPdfTipo`/`generarSiFalta`/`regenerarPdfTipo`, `guardarPdfGenerado`,
  `generarIdentificadorOperacion`.
- `dao/NumeradorDAO.java` (`siguienteValor`, `SELECT ... FOR UPDATE`).
- `dao/ArchivoBinarioDAO.java` / `dao/DocumentoExpedienteDAO.java`.
- `controladores/ControladorFormalizacionServlet.java`, `controladores/ControladorDocumentosServlet.java`.
- Tablas: `numeradores` (clave='OPERACION'), `prestamos.identificador_operacion`/`numero_contrato`/`numero_pagare`
  (las 3 `UNIQUE`), `archivos_binarios` (`contenido BYTEA`), `documentos_expediente`.
- Parámetros `empresa.*` (razón social, RUC, domicilio, representante, registro BCP, etc.) —
  todavía con datos de prueba, ver pregunta h.

## 5. Dependencias

- **Depende de**: RF11 (la fecha del primer vencimiento y el plan ya calculado, incluido el CFT
  — RF12 solo imprime lo que RF11 ya generó, nunca calcula nada); RF09/RF10 (monto, plazo, TEA
  aprobados); el expediente del cliente (domicilio, teléfono, cédula).
- **Lo afecta un cambio en**: `ServicioMotorFinanciero` (motor financiero único — cualquier
  cambio en cómo se calculan cuota/CFT se refleja automáticamente en los 3 PDF, sin tocar las
  plantillas); los parámetros `empresa.*` (cambian el letterhead de los 3 documentos).
- **A quién afecta un cambio acá**: RF13 (checklist de firmados — exige que estos 3 PDF ya
  existan); E6/desembolso (usa el mismo `identificador_operacion` para el comprobante de
  desembolso); E9 (constancia de devolución de pagaré, mismo patrón de versionado).

## 6. Análisis

### 11/10/2026 — revisión completa de RF12 contra el texto firmado (pedido de Joel, solo lectura)

**Método**: lectura completa de `ServicioFormalizacion.java` (sección de PDF, líneas ~770-1240),
los 3 `.jrxml`, `NumeradorDAO.java`, `ControladorDocumentosServlet.java`, `FiltroAutorizacion.java`,
`ServicioAutorizacion.construirMatriz`, `bd/01_esquema.sql` (tablas `numeradores`,
`archivos_binarios`, `prestamos`), y `docs/00_especificacion_maestra.md` (§8 "Qué debe cuadrar
en cada imprimible", §16.3 "Lista de verificación antes de atender clientes reales").

### a) Dónde está y cómo se generan — respondido en la sección 2.

### b) Número de operación único

**Formato**: `CC-AAAA-NNNNN` (`ServicioFormalizacion.generarIdentificadorOperacion`, línea 647-650:
`String.format("CC-%d-%05d", anio, secuencia)`) — año de 4 dígitos, secuencia de 5 con ceros a
la izquierda. Coincide exacto con Doc. 00 §838/RF-18 y con el texto de Joel.

**Cómo se genera**: `NumeradorDAO.siguienteValor(conexion, "OPERACION", anio)` — `SELECT
ultimo_valor FROM numeradores WHERE clave = 'OPERACION' AND anio = ? FOR UPDATE`, crea la fila
en 0 si no existe (`ON CONFLICT DO NOTHING`), suma 1 y la devuelve, todo dentro de la misma
transacción que crea el préstamo.

**Qué garantiza que no se repita**:
1. **Concurrencia (dos formalizaciones a la vez)**: el `FOR UPDATE` bloquea la fila
   (clave, año) — si dos formalizaciones piden un número en el mismo instante, la segunda
   transacción espera hasta que la primera haga `commit` (o `rollback`) antes de leer/sumar, así
   que nunca ven el mismo valor. Esto es el mismo patrón ya documentado para `codigo_solicitud`
   (Doc. 00 §10: "Numeración correlativa con bloqueo de fila").
2. **Restricción UNIQUE en la base**: `prestamos.identificador_operacion VARCHAR(50) NOT NULL
   UNIQUE` (`bd/01_esquema.sql:321`) — y también `numero_contrato`/`numero_pagare` (líneas
   323-324) son `UNIQUE` cada uno. Es el respaldo final: aunque el `FOR UPDATE` ya debería
   impedir cualquier duplicado, si alguna vez fallara, el `INSERT` del préstamo rechazaría el
   número repetido con una violación de restricción (el llamador la ve como un error genérico
   "No se pudo completar la operación", no un mensaje específico de duplicado — ver sección 10).
3. **Atomicidad**: el incremento del numerador (`UPDATE numeradores SET ultimo_valor =
   ultimo_valor + 1`) es parte de la MISMA transacción que crea el préstamo y los 3 PDF — si
   algo falla después y se hace `rollback`, el incremento también se revierte. No quedan
   números "quemados" por intentos fallidos (a diferencia de una secuencia de PostgreSQL nativa,
   que no revierte con el rollback — este diseño lo evita a propósito).

**¿Reinicia por año?** Sí — la clave primaria de `numeradores` es `(clave, anio)`
(`bd/01_esquema.sql:741`), así que cada año arranca una fila nueva en 0. El año que se usa es el
de la fecha de HOY al formalizar (`generarIdentificadorOperacion(conexion, hoy.getYear())`), no
el año de la solicitud ni el de la aprobación.

**Al regenerar o re-emitir, ¿mantiene el número?** Sí, siempre. Ni `regenerarDocumentos` ni
`regenerarSoloPdfs` llaman a `generarIdentificadorOperacion` de nuevo — el número, el número de
contrato y el número de pagaré quedan fijos desde la primera formalización, para toda la vida
del préstamo (los 3 son, de hecho, el mismo valor — ver nota más abajo).

**Nota (no es un hallazgo, es una decisión de diseño ya tomada, D-68)**: `numero_contrato` y
`numero_pagare` no son dos números independientes del contrato y del pagaré — los tres
(`identificador_operacion`, `numero_contrato`, `numero_pagare`) se fijan al mismo valor
`CC-AAAA-NNNNN` (línea 491-492: "una sola operación, tres documentos"). El texto del
anteproyecto pide "un número de operación único" (singular) para los 3 documentos, no un número
por documento — esta decisión ya es exactamente lo que el texto pide, sin necesidad de 3
numeradores distintos.

### c) Qué datos lleva cada PDF y de dónde salen

Todos los datos se calculan una sola vez en `construirParametrosReporte` (compartido por los 3
PDF) y en `construirClausulasContrato` (solo contrato) — ningún `.jrxml` calcula nada, solo
muestra lo que le llega ya formateado (cita del comentario de `pagare.jrxml`, sección 2).

| Dato | De dónde sale | En qué PDF |
|---|---|---|
| Cliente (nombre, C.I., domicilio, teléfono) | `Persona` (vía `solicitud.getIdCliente()`) | Contrato (partes), pagaré (deudor), plan (encabezado) |
| Monto en números y letras | `prestamo.getMontoAprobado()` + `ConversorNumerosALetras.comoLetras(...)` en MAYÚSCULAS | Contrato (cláusula primera), plan (encabezado) |
| Plazo | `prestamo.getPlazoMeses()` | Contrato, pagaré, plan |
| TEA / TNA | `prestamo.getTasaTea()`/`getTasaTna()` | Contrato (cláusula tercera), plan |
| CFT | `prestamo.getCft()` (recalculado con el mismo cotizador, RF11) | Contrato (cláusula tercera), plan |
| Cuota (la primera, con IVA) | `cuotas.get(0).getTotalProgramado()` | Contrato (no explícito en el pagaré, que muestra el total) |
| Total a pagar (en números y letras) | Suma de `cuota.getTotalProgramado()` de TODO el plan | Contrato (cláusula tercera), pagaré (monto del pagaré), plan |
| IVA | Suma de `cuota.getTributosProgramados()` | Plan (columna "IVA 10 %" y total) |
| Primer vencimiento | Fecha EFECTIVA de la cuota 1 (ya corrida, RF11) — no la teórica elegida | Contrato (cláusula segunda), plan (primera fila), **y desde el 29/10/2026 también el comprobante de desembolso** (C12: era el único que mostraba la elegida). El cálculo tiene un solo dueño: `ServicioFormalizacion.primeraFechaVencimientoEfectiva` |
| Día al que se anclan las cuotas **siguientes** | El día de la fecha **ELEGIDA** al formalizar — `generarPlanPagos` hace `fechaPrimerVencimiento.plusMonths(k-1)` y recién sobre esa teórica corre a día hábil, cuota por cuota. Si el mes no tiene ese día (29, 30, 31), `plusMonths` resuelve al **último día del mes** | Contrato (cláusula segunda, desde el 29/10/2026 — C3). **Antes decía "el mismo día de cada mes", que describía mal el cálculo cuando elegida ≠ efectiva** |
| Plan completo (cuotas) | `listarCuotasVigentes` — la MISMA lista que usa la pantalla `ficha.jsp` | Plan de pagos (una fila por cuota) |
| Firmas | Bloques fijos en la plantilla (nombre + C.I. de cada parte) | Los 3 (contrato y pagaré con línea de firma; plan con "Recibí copia...") |
| Fecha y lugar | `FECHA_FORMALIZACION_LETRAS` (fecha de hoy, en letras, **con su preposición y artículo** — ver abajo) + "Asunción" fijo en el texto | Contrato (encabezado de las partes). **El pagaré y el plan NO usan esta fecha**: imprimen `FECHA_FORMALIZACION` en dígitos (`dd/MM/aaaa`) |

**La fecha en letras del contrato: quién decide cada palabra** (B11 + B103, 01/11/2026).
`ConversorFechasALetras` es el único dueño de esa frase, y devuelve **la preposición, el
artículo, el día y la concordancia juntos** (`comoFechaConArticulo`):

- día 1 → *"**al primer** día del mes de..."* (singular, convención de los imprimibles legales);
- cualquier otro día → *"**a los** veintiún **días** del mes de..."*, con el día **apocopado**
  cuando termina en uno (21 → "veintiún", 31 → "treinta y un"), porque precede a un sustantivo
  masculino.

El texto de la cláusula (`ServicioFormalizacion.construirClausulasContrato`) ya **no** escribe el
`"a los "`: lo pide junto con la fecha. Antes lo tenía fijo y un préstamo formalizado un día 1
imprimía *"a los primer día del mes de..."* (B103). El apócope es B11 y vive en
`ConversorNumerosALetras.comoLetrasApocopado` — **método aparte**: el `comoLetras` de los montos
no se toca (ahí "veintiuno" es correcto).

**¿Coinciden exactamente con el plan de la pantalla?** Sí — `generarPdfPlanDePagos` y
`ControladorFormalizacionServlet.mostrarFicha` llaman al MISMO método
(`ServicioFormalizacion.listarCuotasVigentes`, una sola fuente de datos), así que no hay forma
de que la tabla del PDF y la de la pantalla difieran (no hay dos cálculos paralelos que puedan
desincronizarse).

### d) Normativa

- **Tope de tasa**: la cláusula TERCERA dice textualmente "La tasa pactada no supera la tasa
  máxima vigente publicada por el Banco Central del Paraguay a la fecha de este contrato" — y
  esto ya se revalida en el código antes de formalizar (RN-15, `ServicioFormalizacion.generarDocumentos`
  líneas 349-361, visto en RF11).
- **Ley 6624/2020** (pago anticipado sin intereses futuros ni penalidad): citada explícitamente
  en la cláusula SEXTA del contrato y en la nota 3 del plan de pagos ("Puede cancelar o adelantar
  pagos en cualquier momento sin penalidad ni intereses futuros (Ley 6624/2020)").
- **Ley 6380/2019** (RNF18 del anteproyecto, IVA sobre intereses): no se cita por su número en
  ningún PDF, pero el cálculo (10 % de IVA sobre el interés de cada cuota) está aplicado en los
  3 — el plan de pagos muestra la columna "IVA 10 %" explícitamente. La ley en sí no exige
  citarse por número en el documento, solo que el cálculo se respete — se respeta.
- **Ley 6534/2020** (protección de datos crediticios, buró): citada en la cláusula NOVENA
  (información crediticia).
- **TEA/CFT mostrados**: sí, ambos explícitos en la cláusula TERCERA y en el plan de pagos —
  cumple lo que pide Doc. 00 §838 ("TEA, TNA, CFT").
- **Mínimo de un pagaré** (promesa incondicional de pago, monto en letras, vencimiento, lugar de
  pago, firma): el `pagare.jrxml` tiene los 5 elementos — "Pagaré sin protesto a... la suma de
  [monto en letras] ([monto en números])", "Vence el [fecha]", lugar de pago = domicilio de la
  empresa (de los parámetros `empresa.*`), y el bloque de firma del DEUDOR con nombre y C.I.
- **Hallazgo — no es un bug, es un pendiente ya documentado en el propio proyecto**:
  `docs/00_especificacion_maestra.md` §16.3 ("Lista de verificación antes de atender clientes
  reales"), punto 6: *"Reemplazar los modelos de contrato y pagaré por los revisados por un
  abogado."* — el texto legal actual (cláusulas, redacción del pagaré) es el que escribió el
  equipo del proyecto, razonable y con las citas de ley correctas que se verificaron, pero
  **todavía no tuvo una revisión profesional de un abogado**, algo que el propio proyecto ya
  sabe y tiene anotado como paso previo a operar con clientes reales — no es parte del alcance
  de RF12 en sí (que solo pide "generar el PDF... con un número único", algo que se cumple), pero
  es relevante para la pregunta de "normativa" que hizo Joel.

### e) Seguridad

- **Quién puede ver/descargar**: `GET /documentos/{id}` está protegido por `FiltroAutorizacion`
  con el permiso `CARGAR_DOCUMENTOS_CONSULTA_BURO` (`FiltroAutorizacion.java:45`) — TOTAL para
  gerente y oficial, **NINGUNO para la cajera** (`ServicioAutorizacion.java:40,79,106`). El
  administrador, como siempre, TOTAL.
- **Hallazgo — se puede pedir el PDF de OTRA operación cambiando el número en la URL**:
  `ControladorDocumentosServlet.descargar` (líneas 287-312) solo verifica que el `idDocumento`
  exista — no valida que el documento pertenezca a una solicitud/préstamo del oficial que lo
  pide, ni ningún otro criterio de "dueño". Cualquier usuario con perfil OFICIAL_CREDITO o
  GERENTE_ADMINISTRADOR (hay más de uno de cada en el sistema) puede ver el contrato, pagaré o
  plan de CUALQUIER cliente simplemente cambiando el número en `/documentos/123`,
  `/documentos/124`, etc. — no es un agujero nuevo de RF12 puntualmente: es el mismo criterio
  que ya tiene el resto del sistema para oficiales y gerentes (todos ven todos los clientes y
  solicitudes, no hay una "cartera" exclusiva por oficial en las pantallas tampoco) — pero vale
  dejarlo explícito porque Joel preguntó puntualmente por esto.
- **Dónde se guardan los archivos**: en la base de datos, no en el sistema de archivos —
  `archivos_binarios.contenido BYTEA` (`bd/01_esquema.sql:173`), con `hash_sha256` guardado para
  verificar integridad y `origen_archivo = 'JASPER'` (distinto de `'CARGADO'`, para diferenciar
  un PDF generado por el sistema de un escaneo subido a mano). El navegador nunca recibe una
  ruta física del servidor (comentario explícito en `ControladorDocumentosServlet.java:34-35`).
- El PDF se sirve `inline` (se abre en la pestaña, no se descarga a la carpeta Descargas por
  defecto) con `X-Content-Type-Options: nosniff`.

### f) "Re-emitir los PDF (sin cambiar plan)"

`ServicioFormalizacion.regenerarSoloPdfs` (ruta `/formalizacion/{id}/regenerar-pdf`): re-emite
los 3 PDF (contrato, pagaré, plan) con la plantilla y los datos ACTUALES, sin tocar el plan de
cuotas ni el número de operación — pensado para aplicar una corrección de diseño del reporte a
un préstamo ya formalizado (por ejemplo, si se corrige un texto de la plantilla después). A
diferencia de `regenerarDocumentos` (RF11), no exige que no haya documentos firmados cargados,
porque el plan no cambia (no hay riesgo de que el firmado quede desincronizado del vigente).

**Queda auditoría**: sí, en dos niveles — un evento resumen `REGENERAR_DOCUMENTOS_FORMALIZACION`
("Re-emitió los PDF de CC-... (sin cambiar plan ni número).") y, por cada uno de los 3 PDF, un
evento `GENERAR_PDF_FORMALIZACION` individual ("Generó "contrato_CC-....pdf" para CC-....").
Cada PDF reemplazado queda como una fila VERSIONADA en `documentos_expediente` (el anterior se
marca `REEMPLAZADO`, nunca se borra) — se puede reconstruir el historial completo de qué PDF
existió en cada momento.

### g) Textos y menciones a herramientas de desarrollo

Revisados los 3 `.jrxml` completos (texto estático y expresiones): **sin voseo** (todo en
tercera persona o fórmulas legales neutras, no hay instrucciones dirigidas al cliente que
necesiten "usted"/"vos" porque el contrato/pagaré no le habla directamente al lector, describe
obligaciones de las partes) y **sin ninguna mención a Claude, Anthropic, JasperReports/Jaspersoft,
ni ningún otro nombre de herramienta de desarrollo** — el pie de página solo dice "Emitido por:
[usuario] — [fecha]" y el número de página, nada más.

### h) Cumplimiento

El texto del anteproyecto se cumple **sin excepción**: los 3 PDF se generan (contrato, pagaré,
plan de pagos), cada uno con el número de operación único `CC-AAAA-NNNNN`, protegido contra
duplicados por bloqueo de fila + restricción UNIQUE + atomicidad transaccional. No se encontró
ninguna parte del texto sin cumplir.

Dos observaciones, ninguna de las dos es un incumplimiento de RF12:
1. El IDOR de la sección e) (cualquier oficial/gerente puede ver el PDF de cualquier operación
   cambiando el número en la URL) — coherente con el resto del sistema, pero vale que Joel lo
   confirme como aceptable o decida acotarlo.
2. El pendiente ya documentado en Doc. 00 §16.3 (revisión legal profesional de las plantillas
   antes de atender clientes reales) — fuera del alcance funcional de RF12, pero relevante para
   la pregunta de normativa.

## 7. Decisiones

Las dos observaciones de la sección 6h (IDOR de documentos; revisión legal pendiente de las
plantillas) quedan sin acción por ahora — ninguna exige programar algo para que RF12 cumpla el
texto firmado. Las 6 mejoras menores de la prueba de pantalla (sección 10, a-f) quedan
anotadas, sin corregir en esta vuelta.

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 01/11/2026 | **B11 + B103**: la fecha en letras del contrato trae su preposición y artículo del mismo dueño que el día — "al primer día" (día 1) y "a los veintiún días" (apócope). **Probado en pantalla el 01/11/2026 12:39** con `CC-2026-00006` (Ana, formalizada un día 1) | `4ef5194` | — |
| 01/11/2026 | **B13**: `direccion_domicilio` y `barrio` se guardan capitalizados (RF02), así que el contrato ya no imprime "mburicao 444". **Probado en pantalla el 01/11/2026** re-emitiendo los PDF de `CC-2026-00006` (mismo número, D-94): "con domicilio en General Santos 123, Jara, Asunción" | `0d0b86d` | — |
| 11/10/2026 | Reprueba del Lote 1 (pedido de Joel, punto E): firma de "QUIEN LE ATENDIÓ" (nombre y C.I. del usuario que generó los documentos) agregada en el contrato (fila propia, sin quitar la del representante) y en el plan de pagos (además de "Recibí copia" del cliente) — nuevo parámetro `USUARIO_EMISOR_CEDULA`. El pagaré queda sin cambios (solo deudor/codeudor, confirmado con Joel: es un título de crédito unilateral) | *(pendiente)* | — |
| 11/10/2026 | **Reprueba del Lote 1: A.1 confirmado OK en el CONTRATO, pero el PAGARÉ seguía mal** ("GUARANÍES" duplicado, singular/plural sin aplicar) — causa real encontrada: el `.jasper` (lo único que el sistema carga en tiempo de ejecución) nunca se recompiló después de editar `pagare.jrxml`/`plan_pagos.jrxml` — el proyecto exige mantener `.jrxml` y `.jasper` sincronizados a mano (`docs/REGLAS_DESARROLLO.md` §6) y ese paso se omitió. Recompilados los 37 reportes del sistema (`herramientas/compilar_reportes.sh`); agregado un control permanente (`ReportesCompiladosTest`, falla `mvn test` si un `.jasper` queda desactualizado) — ver `docs/REGLAS_DESARROLLO.md` §6 y el plan de consolidación, punto B | *(pendiente)* | — |
| 11/10/2026 | Consolidación RF08-RF19 (Lote 1): monto en letras con "DE GUARANÍES" correcto (A.1), pagaré en singular sin codeudor (A.2), domicilio sin la referencia coloquial en los documentos legales (B.15) | *(pendiente)* | — |
| 11/10/2026 | Probado en pantalla completo con CC-2026-00001: los 3 PDF, mismo número, cifras idénticas a la pantalla, pagaré completo, sin voseo ni menciones a herramientas | *(pendiente)* | `rf12-probado` |
| 11/10/2026 | Análisis completo contra el texto firmado, sin cambios de código | *(pendiente)* | — |

## 9. Pruebas realizadas en pantalla

**11/10/2026 (Joel), después del Clean and Build, con CC-2026-00001:**
- Contrato (2 páginas), pagaré y plan de pagos: los 3 con "N.º CC-2026-00001" — el contrato
  remite al plan ("Plan de Pagos N.º CC-2026-00001", cláusula SEGUNDA) y el pagaré al contrato
  ("Contrato de Préstamo N.º CC-2026-00001") con el mismo número en los 3 — confirma la decisión
  D-68 (sección 6b) de un solo número para toda la operación.
- Cifras idénticas a la pantalla: monto 5.000.000, 12 cuotas, cuota 483.199, TEA 27 %, TNA
  24,1413 %, CFT 30,0383 %, total 5.745.449, primer vencimiento 24/11/2026, última cuota
  25/10/2027 — confirma que `construirParametrosReporte` y la pantalla leen exactamente los
  mismos campos del préstamo/plan (sección 6c). Las cuotas que cayeron en fin de semana se
  corrieron en el plan impreso, igual que en la pantalla (RF11).
- Pagaré: monto en números y letras, vencimiento 25/10/2027 (última cuota), lugar y fecha de
  emisión, beneficiario (la empresa), lugar de pago (domicilio de la empresa) y firma del
  librador — los 5 elementos mínimos confirmados en pantalla (sección 6d).
- Sin voseo y sin ninguna mención a herramientas de desarrollo en ninguno de los 3 PDF
  (sección 6g).

**Resultado: RF12 queda probado completo.** 6 mejoras menores encontradas (sección 10, a-f), NO
corregidas en esta vuelta.

## 10. Pendientes y trabajo futuro

Mejoras menores encontradas en la prueba de pantalla del 11/10/2026 — **a, c y d corregidos el
11/10/2026 en la consolidación RF08-RF19 (Lote 1, items A.1, A.2 y B.15); a quedó confirmado OK
en pantalla para el CONTRATO en la reprueba, pero el PAGARÉ seguía mostrando el error (causa:
`.jasper` sin recompilar, ver Historial) — corregido de nuevo, pendiente la reprueba puntual del
pagaré; b y e quedan para el Lote 2 (estilo común de PDF, con la aprobación de Joel primero)** —
ver [consolidacion_RF08_RF19.md](procesos/consolidacion_RF08_RF19.md):

a) ~~**Monto en letras de millones redondos**: "CINCO MILLONES GUARANÍES" en vez de "CINCO
   MILLONES DE GUARANÍES"~~ — corregido: `ServicioFormalizacion` ahora llama a
   `ConversorNumerosALetras.comoLetrasConGuaranies(...)` para `MONTO_APROBADO_LETRAS`/
   `TOTAL_A_PAGAR_LETRAS` (en vez de `comoLetras` + " GUARANÍES)" a mano), y el contrato/plan ya
   no agregan el sufijo por su cuenta. **Hallazgo de paso**: `pagare.jrxml` antes anteponía
   "GUARANÍES " a `TOTAL_A_PAGAR_LETRAS` ("la suma de GUARANÍES ...") — con el parámetro ya
   incluyendo la palabra, quedaba duplicado ("GUARANÍES CINCO MILLONES DE GUARANÍES"); se quitó
   ese prefijo del `.jrxml`. Pruebas unitarias agregadas para 15.000.000, 2.500.000 y
   1.000.000.000 (`ConversorNumerosALetrasTest`).
b) Espacio en blanco grande en la página 1 del contrato (debajo del recuadro) y en el plan
   (antes de la tabla) — revisar las alturas fijas de `<rectangle>`/bandas en `contrato.jrxml` y
   `plan_pagos.jrxml` (comentarios ya existentes en el archivo sobre este mismo tipo de ajuste
   con `positionType="Float"`, aplicado en otras partes pero no en estas dos). **Lote 2** (C.19).
c) ~~La referencia de domicilio tipo "(casa blanca con portón verde)" aparece en el contrato y en
   el pagaré~~ — corregido: `ServicioClientes.domicilioSinReferencia` (pura, con pruebas) quita el
   paréntesis final del domicilio compuesto antes de llenar `CLIENTE_DOMICILIO`/
   `CODEUDOR_DOMICILIO` en `ServicioFormalizacion` (y en `ServicioDesembolso`, ver
   [RF17_comprobante_desembolso.md](RF17_comprobante_desembolso.md)); la ficha del cliente sigue
   mostrando la referencia completa (uso operativo, sin cambios).
d) ~~Pagaré con un solo deudor: "PAGARÉ (pagaremos)"/"a nuestra entera satisfacción" en plural
   siempre~~ — corregido: ahora condicionado a `$P{TIENE_CODEUDOR}` ("PAGARÉ (pagaré)"/"a mi
   entera satisfacción" sin codeudor).
e) El logo de la empresa aparece en el contrato pero no en el pagaré ni en el plan de pagos.
   **Causa confirmada**: solo `contrato.jrxml` declara el parámetro `LOGO` y el `<image>` que
   lo muestra; `pagare.jrxml`/`plan_pagos.jrxml` no lo declaran (`GeneradorInformesPdf.logoComoBytes`
   ya está preparado para que cualquier `.jrxml` lo use, "los que todavía no lo declaran
   simplemente ignoran esta clave" — comentario en el código). La corrección sería agregar el
   parámetro y el `<image>` a los otros dos `.jrxml`, igual que en el contrato.
f) **Datos de la empresa — respuesta a la pregunta de Joel**: los 3 PDF ya toman TODO (razón
   social, RUC, domicilio, teléfono, WhatsApp, correo, registro BCP, representante legal) desde
   Parámetros (`empresa.razon_social`, `empresa.ruc`, `empresa.domicilio`, etc. —
   `bd/03_parametros.sql:94-108`) — nada está fijo en las plantillas `.jrxml`. Hoy esos
   parámetros tienen los datos de ejemplo del proyecto (RUC 80123456-7, Registro BCP Res. SB
   N.º 0147/2026, el resto también de ejemplo) — coincide exactamente con el punto 1 y 3 de la
   lista de verificación de `docs/00_especificacion_maestra.md` §16.3 ("Cargar razón social,
   RUC, domicilio y representante legal reales"; "Cargar registro BCP y SIRO reales"). Cuando
   Joel confirme los datos reales con el dueño, se cambian desde Parámetros (solo el
   administrador puede editarlos) **sin reprogramar nada** — se reflejan automáticamente en los
   3 PDF la próxima vez que se generen.
- **Firma de "QUIEN LE ATENDIÓ" (punto E)**: implementada y **confirmada en pantalla el
  11/10/2026** (Joel): el contrato muestra la fila y el plan de pagos "Entregado por...", sin
  mover ni quitar la firma del representante ni el "Recibí copia" del cliente.

**Detalles menores encontrados en la reprueba del 11/10/2026 (Joel) — se corrigen junto con el
Lote 2, sin tocar ahora:**

a) Pagaré con un solo deudor dice "PAGARÉ (pagaré) sin protesto" — el paréntesis queda raro.
   Superado por el retiro de codeudor/garante (18/10/2026, commit `6e7da13`): el ternario de
   `TIENE_CODEUDOR` se eliminó entero, el texto queda fijo en "Pagaré sin protesto" — ver
   `docs/funcionamiento/RF05_solicitud.md` §10.
d) **Pregunta de Joel: "Quien le atendió" hoy es quien re-emite — ¿debería quedar fijo en quien
   formalizó?** Verificado en el código: **ya está fijo en quien formalizó, no en quien
   re-emite** — `generarPdfContrato`/`generarPdfPagare`/`generarPdfPlanDePagos` (los 3 métodos
   que arman el contenido del PDF, llamados tanto al formalizar como al regenerar/re-emitir)
   siempre llaman a `construirParametrosReporte(conexion, prestamo, prestamo.getFormalizadoPor())`
   — nunca con el usuario de la sesión que ejecuta la regeneración/re-emisión. Ese otro usuario
   solo queda registrado en `archivos_binarios.cargado_por` (quién subió ESE archivo al
   expediente) y en la auditoría de la acción (`REGENERAR_DOCUMENTOS_FORMALIZACION`), nunca en
   el contenido impreso. **No hace falta ningún cambio** — si en la reprueba se vio un nombre
   distinto, probablemente fue re-emitido con la sesión del mismo usuario que formalizó
   (`formalizadoPor` no cambia salvo que se formalice de nuevo, lo que no ocurre al regenerar).
   Confirmar en la próxima reprueba: regenerar/re-emitir con un usuario DISTINTO al que
   formalizó y verificar que el nombre impreso no cambia.

## 11. Guion para la defensa

**Confirmado en pantalla el 11/10/2026 (sección 9), con CC-2026-00001.**

1. Mostrar los 3 PDF generados al formalizar SOL-2026-0001 (CC-2026-00001): contrato, pagaré,
   plan de pagos — mismo número en los 3.
2. Señalar en el contrato: partes (cliente), monto en números y letras, plazo,
   TEA/TNA/CFT, primer vencimiento, cláusula de pago anticipado (Ley 6624/2020), firmas.
3. Señalar en el pagaré: promesa de pago, monto en letras, vencimiento (última cuota), lugar de
   pago, firma del deudor.
4. Mostrar el plan de pagos con la columna de IVA 10 % y el total, y confirmar que coincide
   exactamente con la tabla de la pantalla `ficha.jsp` (misma fuente de datos).
5. Mostrar cómo se protege el número: abrir `numeradores` (o explicarlo) y el `UNIQUE` de
   `prestamos.identificador_operacion`.
6. Entrar como cajera (forzando la URL de `/documentos/{id}`) y mostrar el 403.
7. Re-emitir los PDF sin cambiar el plan (`/regenerar-pdf`) y mostrar la auditoría con los 4
   eventos (el resumen + los 3 individuales).
