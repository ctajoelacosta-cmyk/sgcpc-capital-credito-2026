# RF04 — Autorización firmada y consulta al buró de información crediticia

**Estado: cerrado. Probado en pantalla completo el 10/10/2026 (ver §9), con todas las
correcciones encontradas en el camino ya aplicadas y confirmadas. Tag `rf04-probado`.**

## 1. Qué exige el anteproyecto

| ID | Texto textual (`docs/RF_ANTEPROYECTO.md`) |
|---|---|
| RF04 | El sistema debe permitir registrar la autorización firmada del cliente y la consulta al buró de información crediticia, y no permitir la consulta sin autorización. |

RF04 depende del expediente de documentos (RF03): la autorización firmada se carga como un
documento más del expediente (**fila 1** desde el 29/10/2026, decisión de Joel — antes fila 4, ver B90 en `FASE2_PLAN.md`), con su propio tipo reservado.

## 2. Cómo funciona

La ficha del cliente, pestaña ③ "Consulta al buró", tiene dos pasos:

| Paso | Qué ve el usuario | Qué pasa por dentro |
|---|---|---|
| **Paso A — Autorización**: "Generar autorización" | Se genera un PDF con número único (`AUT-aaaa-NNNNN`), texto legal fijo, vigente hasta hoy + `buro.dias_vigencia_consentimiento` (30 días) | `ServicioConsultaCrediticia.generarAutorizacionBuro`: toma número de `numeradores` y audita `GENERAR_AUTORIZACION_BURO` (sin tabla nueva) |
| **Paso A — Cargar autorización firmada** | Sube el escaneo firmado; solo se puede el **mismo día** que se generó el PDF (si no: "Esta autorización es de otro día. Generá una nueva...") | `cargarAutorizacionFirmada`: valida `autorizacionGeneradaHoy`, sube el documento (`ServicioDocumentos.cargarDocumento`, tipo reservado `AUTORIZACION_CONSULTA_BURO`) y registra el consentimiento (`consentimientos_crediticios`, `fecha_consentimiento = now()`) |
| **Paso A — Autorización vencida** | "Vencida el dd/mm/aaaa. No se puede reutilizar la autorización vieja: generá y cargá una nueva." | `validarConsultaPermitida` compara `fecha_consentimiento + 30 días` contra hoy |
| **Paso B — Cargar la respuesta de Informconf** | Sube el PDF de respuesta del buró (botón "Cargar respuesta"/"Reemplazar", desde el 10/10/2026 — antes "Cargar informe"); bloqueado hasta que el Paso A esté vigente | `cargarInformeBuro`: sube el documento (tipo reservado `RESULTADO_CONSULTA_BURO`, ruta dedicada `/documentos/cargar-informe-buro`). Solo adjunta el PDF — **todavía no registra la consulta** |
| **Paso B — Registrar consulta** | Formulario con puntaje, clasificación (A-E), "¿Tiene mora?" (obligatorio) y resumen; el botón "Registrar consulta" permanece deshabilitado hasta que el informe esté cargado | `registrarConsulta`: valida indicador de mora e informe cargado, **vuelve a validar la autorización vigente dentro de la misma transacción**, e inserta la fila en `consultas_crediticias` |
| **Paso B — Consulta ya registrada** (hallazgo de Joel probando en pantalla, 10/10/2026) | El formulario queda oculto; solo se ve el resumen ("Consulta registrada: ...") y "Siguiente: Crear solicitud". Un botón "Registrar una nueva consulta" vuelve a mostrar el formulario (ej. para una consulta más reciente) | Solo JS (`ficha.jsp`): nada cambia del lado del servidor, `pasoBuroCompletoFicha` ya existía y decidía el badge "✅ Registrada" |
| **Intentar registrar la consulta sin autorización vigente** (forzando el POST, o con la autorización vencida en el medio) | Rechazado con el mensaje de "No hay una autorización de consulta vigente..." o "La autorización... venció el...", sin guardar nada | `validarConsultaPermitida` corre de nuevo dentro de `registrarConsulta`, no confía en lo que la pantalla tenía cargado |

## 3. Auditoría

| Evento | Entidad | Cuándo |
|---|---|---|
| `GENERAR_AUTORIZACION_BURO` | PERSONA | Se generó el PDF de autorización (también marca "generada hoy" para habilitar la carga de la firmada) |
| `CARGAR_DOCUMENTO` / `NUEVA_VERSION_DOCUMENTO` | DOCUMENTO_EXPEDIENTE | Se subió el escaneo de la autorización firmada o el informe de Informconf (mismo evento que cualquier documento del expediente) |
| `REGISTRAR_CONSENTIMIENTO` | CONSENTIMIENTO_CREDITICIO | Se registró el consentimiento (al cargar la autorización firmada) |
| `REGISTRAR_CONSULTA_BURO` | CONSULTA_CREDITICIA | Se registró el resultado de la consulta a Informconf |

"Quién" consultó queda en `consultas_crediticias.consultado_por` — desde el 10/10/2026 también
se muestra en la tabla rápida de "Historial de consultas" de la ficha (columna "Consultado
por", `ConsultaCrediticiaDAO.listarPorPersona` con `LEFT JOIN usuarios`), además de en el informe
dedicado "Consultas al buró" (`ConsultaCrediticiaDAO.listarParaInforme`, C-4.4), que además marca
si la autorización seguía vigente en el momento exacto de esa consulta (no hoy).

## 4. Dónde está

- `servicios/ServicioConsultaCrediticia.java` — toda la lógica: `generarAutorizacionBuro`,
  `cargarAutorizacionFirmada`, `cargarInformeBuro`, `registrarConsulta`, `validarConsultaPermitida`,
  `calcularFechaVencimiento`, `tieneAutorizacionVigente`, `tieneConsultaConEvidenciaRegistrada`.
- `dao/ConsultaCrediticiaDAO.java` (`listarPorPersona` con `LEFT JOIN usuarios`, desde el
  10/10/2026) / `dao/ConsentimientoCrediticioDAO.java`.
- `modelos/ConsultaCrediticia.java` — campo `nombreConsultadoPor` (desde el 10/10/2026, solo lo
  completa `listarPorPersona`; null en `insertar`/`listarPorSolicitud`, que no lo necesitan).
- `controladores/ControladorClientesServlet.java` (`/clientes/{id}/consulta-buro`),
  `controladores/ControladorDocumentosServlet.java` (`/documentos/cargar-autorizacion-firmada`,
  `/documentos/cargar-informe-buro`).
- `webapp/WEB-INF/vistas/clientes/ficha.jsp` — pestaña ③, tarjetas "Paso A" y "Paso B ·
  Resultado de la consulta a Informconf" (rótulo actualizado el 10/10/2026).
- `infraestructura/TextoLegalAutorizacionBuro.java` — texto legal único del PDF de autorización.
- Tablas: `consentimientos_crediticios`, `consultas_crediticias`, `documentos_expediente` (tipos
  reservados `AUTORIZACION_CONSULTA_BURO`/`RESULTADO_CONSULTA_BURO`).
- Parámetro: `buro.dias_vigencia_consentimiento` = 30 (editable solo por el administrador).

## 5. Dependencias

- **Depende de**: RF03 (expediente de documentos — la autorización es un documento más, con su
  versionado); RF36/RNF11 (sesión y permisos, cualquier ruta de este flujo exige sesión y el
  permiso del perfil sobre `/clientes`/`/documentos`).
- **Lo afecta un cambio en**: `ServicioDocumentos.cargarDocumento` (versionado de documentos,
  usado por los dos tipos reservados de este flujo).
- **A quién afecta un cambio acá**: RF05/RF06 (la solicitud de crédito exige una consulta al
  buró con evidencia, posterior a la autorización vigente, antes de poder enviarse a
  aprobación — `obtenerParaVincularSolicitud`); RF08 (el resumen de la solicitud para el gerente
  muestra el resultado del buró); RF32 (informe "Consultas al buró", C-4.4).

## 6. Análisis

### 10/10/2026 — revisión completa de RF04 (pedido de Joel, solo lectura, sin cambios de código)

Joel pidió revisar RF04 contra el texto firmado ("El sistema debe permitir registrar la
autorización firmada del cliente y la consulta al buró de información crediticia, y no permitir
la consulta sin autorización"), puntualmente: cómo se registra la autorización firmada; cómo se
registra la consulta (resultado, fecha, quién, el informe en PDF); si el servidor impide
registrar una consulta sin autorización vigente; qué pasa con la autorización vencida (30 días);
y el punto de la pestaña ③, tarjeta "Informe de Informconf"/botón "Cargar informe".

**Método**: lectura de `ServicioConsultaCrediticia.java` completo, `ConsultaCrediticiaDAO.java`,
`ficha.jsp` (pestaña ③, líneas ~1240-1410), `ControladorClientesServlet`/`ControladorDocumentosServlet`
(rutas `/clientes/{id}/consulta-buro`, `/documentos/cargar-autorizacion-firmada`,
`/documentos/cargar-informe-buro`), y `bd/03_parametros.sql` para el valor real del parámetro de
vigencia. Se verificó además que `ConsultaCrediticiaDAO.insertar` solo se llama desde
`registrarConsulta` (ningún otro camino de inserción a `consultas_crediticias`).

**Resultado, parte del texto → cumple/no cumple/dónde:**

| Parte del texto | ¿Cumple? | Dónde |
|---|---|---|
| "registrar la autorización firmada del cliente" | Cumple | `cargarAutorizacionFirmada` + `registrarConsentimiento` (`consentimientos_crediticios`). Exige que el PDF se haya generado ese mismo día. |
| "registrar... la consulta al buró" — resultado | Cumple | `registrarConsulta`: puntaje, clasificación (A-E), indicador de mora (obligatorio, D-57), resumen. |
| "registrar... la consulta al buró" — fecha | Cumple | Columna `fecha_hora_consulta` (default de la base). |
| "registrar... la consulta al buró" — quién | Cumple | Columna `consultado_por`; visible en el informe C-4.4, no en la tabla rápida de la ficha. |
| "registrar... la consulta al buró" — informe en PDF | Cumple | `idDocumentoResultado` enlaza al documento subido como evidencia (`RESULTADO_CONSULTA_BURO`). |
| "no permitir la consulta sin autorización" | Cumple, en el servidor | `registrarConsulta` revalida `validarConsultaPermitida` dentro de su propia transacción, no solo en la pantalla; único camino de inserción. |
| Autorización vencida (30 días) | Cumple | Parametrizado (`buro.dias_vigencia_consentimiento = 30`); rechazo claro, no se puede reusar la vieja, hay que generar y cargar una nueva. |
| Punto de la pestaña ③ ("Informe de Informconf"/"Cargar informe") | Sin problema | Subir el informe por sí solo no registra la consulta — el paso sigue "🟠 Pendiente" hasta completar el formulario y apretar "Registrar consulta". Los rótulos reales de la pantalla no coincidían textualmente con cómo los nombró Joel — corregido el 10/10/2026, ver §7. |

**Conclusión**: RF04 está cumplido de punta a punta. No se encontró ninguna parte del texto sin
cumplir. El agujero del PDF falso (hallazgo de una sesión anterior, RF03) ya está cerrado.

## 7. Decisiones

- **D-38**: la vigencia de la autorización se calcula desde `fecha_consentimiento` (cuándo se
  firmó), no desde hoy — una reimpresión del PDF no corre la vigencia.
- **D-39**: en E3 la consulta todavía no tiene solicitud asociada (`id_solicitud` NULL); el
  vínculo se resuelve en E4 (`vincularASolicitud`).
- **D-57**: "¿Tiene mora?" es obligatorio al registrar la consulta — sin ese dato no sirve para
  evaluar la solicitud.
- **D-58**: el resultado de la consulta debe adjuntar la evidencia (el informe de Informconf),
  igual que el consentimiento usa su propio documento de evidencia.
- **Hallazgo de una sesión anterior** (antes del 09/10/2026): un PDF cualquiera podía cargarse
  como si fuera la autorización firmada desde "Otros documentos" del expediente genérico —
  cerrado quitando esa sección y creando las dos rutas dedicadas (`cargar-autorizacion-firmada`/
  `cargar-informe-buro`), que rechazan esos dos tipos reservados por la ruta genérica.
- **10/10/2026 (pedido de Joel, tras aprobar el análisis)**: implementadas las 2 mejoras
  anotadas en §10 (pendientes), antes de probar en pantalla.
  1. **Columna "Consultado por"** en la tabla "Historial de consultas" de la ficha: antes esa
     información solo estaba en el informe dedicado C-4.4. `ConsultaCrediticiaDAO.listarPorPersona`
     ahora hace `LEFT JOIN` con `usuarios` (no `INNER JOIN`, mismo criterio que D-152 en
     `AuditoriaDAO`: una consulta nunca debería desaparecer del historial aunque el usuario que
     la hizo ya no exista) y completa `ConsultaCrediticia.nombreConsultadoPor` — campo nuevo,
     solo en ese camino; `insertar`/`listarPorSolicitud` no lo necesitan y quedan sin tocar.
  2. **Rótulos de la pestaña ③, Paso B**, para que coincidan con cómo los nombra Joel: título
     "Paso B · Resultado de la consulta a Informconf" (antes "Paso B · Consulta a Informconf"),
     renglón "1. Respuesta de Informconf (informe en PDF)" (antes "1. Informe de Informconf"),
     botón "Cargar respuesta" (antes "Cargar informe"). Se revisaron los mensajes relacionados y
     se ajustaron los que decían "informe" en vez de "respuesta": el aviso de confirmación antes
     de reemplazar ("Ya existe una respuesta de Informconf vigente...") y el mensaje de error del
     servidor cuando falta cargarla (`ServicioConsultaCrediticia.validarDocumentoResultado`,
     "Primero cargue... la respuesta de Informconf..."); también la etiqueta interna de
     `ServicioClientes.etiquetaDocumentoExpediente` ("Respuesta de Informconf"), aunque hoy no se
     muestra en ningún lado (queda consistente para el futuro). No se encontró ninguna ayuda ⓘ
     con este texto. No se tocó ningún nombre interno (rutas, métodos, ids de HTML) — solo texto
     visible, sin cambiar lógica.
- **10/10/2026 (correcciones de Joel probando en pantalla, antes del commit)**:
  1. **Formulario del Paso B oculto cuando la consulta ya está registrada**: antes quedaba
     visible igual, duplicando el resumen de más abajo. Ahora se oculta y aparece un botón
     "Registrar una nueva consulta" que lo vuelve a mostrar (solo JS, sin cambios de servidor).
  2. **"Documento cargado" dice qué sigue**: el mensaje de éxito al cargar la respuesta de
     Informconf ahora agrega "Complete el puntaje, la clasificación y si tiene mora, y toque
     Registrar consulta." (solo en `procesarCargarInformeBuro`, no en la carga genérica de
     documentos, que sigue con el mensaje de siempre).
  3. **"OK" → "Entendido" en los carteles**: el cartel "Falta la autorización firmada" (pestaña
     ③) usaba el botón por defecto de SweetAlert2 ("OK", nunca se había puesto un texto propio).
     Se revisaron los 19 `Swal.fire(...)` del sistema (fuera de las librerías de terceros): solo
     este y el de "Sesión vencida" (`recursos/js/aviso-sesion.js`, documentado en
     [procesos/login.md](procesos/login.md)) no tenían `confirmButtonText` explícito — los dos
     ahora dicen "Entendido", igual que el resto. El cartel de éxito genérico de `plantilla/pie.jsp`
     ("Aceptar") se queda así a propósito: Joel confirmó la convención "Aceptar" en éxito /
     "Entendido" en error o aviso, ahora documentada en `docs/REGLAS_DESARROLLO.md` §12.
- **10/10/2026 (segunda corrección de Joel, voseo → usted)**: los textos de este RF que usaban
  voseo pasaron a usted: "Primero registre la autorización (Paso A)." (antes "registrá"); "No se
  puede reutilizar la autorización vieja: genere y cargue una nueva en..." (antes "generá y
  cargá"); "Genérela y cárguela en..." (antes "Generála y cargala"); y el mensaje del servidor
  "Esta autorización es de otro día. Genere una nueva para que la fecha coincida con la firma."
  (antes "Generá una nueva..."). El resto de los textos con voseo encontrados en esa misma
  revisión (precalificación, buscador de reportes) no son de este RF — ver `docs/REGLAS_DESARROLLO.md`
  §12 (la regla nueva: siempre usted) y la respuesta de esa sesión para la lista completa.

## 8. Historial de cambios

| Fecha | Commit | Tag | Qué cambió |
|---|---|---|---|
| 14/11/2026 | *(este commit)* | — | **B149**: el botón *"Siguiente: …"* al pie de la pestaña ③ deja de ser `btn-lg btn-primary` y pasa a `btn-outline-primary`, el mismo peso que ya tenía ese control en ① Datos y ② Expediente (es un solo mecanismo desde B88). **"Registrar consulta" no cambia**: sigue `btn-lg`, porque es la acción principal de la pestaña. Estado y razones en `recorrido/FASE2_PLAN.md` § Clientes — tanda 1 de la revisión UX |
| 10/10/2026 | *(este commit)* | `rf04-probado` | Columna "Consultado por"; rótulos de la pestaña ③ Paso B ("Resultado de la consulta a Informconf", "Respuesta de Informconf", "Cargar respuesta"); formulario oculto con la consulta ya registrada + botón "Registrar una nueva consulta"; "Documento cargado" dice qué sigue; "OK" → "Entendido" en los 2 carteles que no tenían texto propio; 4 textos de este RF pasados de voseo a usted |
| (antes del 09/10/2026) | `89e5c43` | `rf03-agujero-cerrado` | Cierra el agujero del PDF falso en el expediente (quita "Otros documentos", rutas dedicadas para autorización/informe) |
| (antes del 09/10/2026) | `f25da5c` | `rf03-parte1` | RF03/RF04: circuito de autorización al buró, Parte 1 |
| (antes del 09/10/2026) | `3f11934` | — | RF03 Parte 2: circuito de la autorización al buró |
| (antes del 09/10/2026) | `6a9f928` | — | RF03 Parte 3: PDF de la autorización al buró |

## 9. Pruebas realizadas en pantalla

**10/10/2026, primera ronda (Joel, en el navegador):**
- **Sin autorización** (Luis Cabrera): cartel "Falta la autorización firmada" y el Paso B
  bloqueado.
- **Con autorización vigente** (Mirtha, `AUT-2026-00003`, vigente hasta 07/11/2026): carga de
  la respuesta en PDF, registro 720 · B · sin mora, estado "✅ Registrada", "Siguiente: Crear
  solicitud", y la columna "Consultado por" en el historial.
- **Textos nuevos de la pestaña ③** (título, renglón, botón).

Esa prueba encontró las 3 correcciones de §7 (formulario oculto, "Documento cargado" con el
siguiente paso, "OK" → "Entendido"), más los 8 textos con voseo pasados a usted.

**10/10/2026, segunda ronda, después del Clean and Build (Joel, en el navegador):**
- Sin autorización: cartel "Falta la autorización firmada" **con "Entendido"** (Luis Cabrera).
- Con autorización vigente: carga de la respuesta en PDF con el cartel que dice qué sigue,
  registro 720 · B · sin mora, estado "Registrada".
- Formulario oculto después de registrar, con "Registrar una nueva consulta" y "Siguiente:
  Crear solicitud".
- Historial desplegable con la columna "Consultado por".
- Pestaña ③ con ✓ y ④ resaltada como siguiente paso.
- Textos en usted.

**Resultado: todo OK. RF04 queda probado completo.**

**Diferida a RF20** (decisión de Joel, no es un pendiente abierto de RF04): autorización
vencida a los 30 días — se prueba cuando se prueben los préstamos en mora/vencimientos de RF20,
que de todos modos necesitan mover fechas varios días hacia adelante.

**Sin probar** (pendiente menor, ver §10): forzar el registro de una consulta sin autorización
vigente con un POST directo (sin pasar por la pantalla), para confirmar en vivo el rechazo del
servidor.

Antes de esta revisión, Joel probó en el navegador el circuito completo de RF03/RF04 en la
sesión en que se cerró el agujero del PDF falso (ver `AVANCE.md`).

## 10. Pendientes y trabajo futuro

- **Prueba diferida a RF20**: autorización vencida a los 30 días (`buro.dias_vigencia_consentimiento`)
  — no se probó en esta ronda; queda para cuando se prueben los préstamos en mora/vencimientos de
  RF20, que de todos modos necesitan mover fechas varios días hacia adelante.
- Forzar el registro de una consulta sin autorización vigente (POST directo, sin pasar por la
  pantalla) — para confirmar en vivo el rechazo del servidor (`validarConsultaPermitida`).

## 11. Guion para la defensa

1. Mostrar la pestaña ③ bloqueada hasta que el expediente (RF03) esté completo.
2. Generar la autorización (PDF con número único), cargarla firmada el mismo día.
3. Mostrar el rechazo si se intenta cargar la firmada otro día distinto al de la generación.
4. Cargar la respuesta de Informconf y registrar la consulta (puntaje, clasificación, mora,
   resumen) — mostrar que no se puede registrar sin esa respuesta cargada.
5. Mostrar "quién" consultó en el historial de la ficha ("Consultado por") y en el informe
   "Consultas al buró" (reportes), que además marca si la autorización seguía vigente en ese
   momento exacto.
6. Si preguntan "¿y si alguien fuerza el formulario sin autorización?": explicar que
   `registrarConsulta` vuelve a validar la vigencia en el servidor, en la misma transacción,
   sin confiar en lo que la pantalla tenía cargado.
