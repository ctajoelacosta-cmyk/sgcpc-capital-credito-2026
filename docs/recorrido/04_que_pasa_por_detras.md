# Anexo — Qué pasa por detrás (Escena 04, tramo 3, rol oficial)

Mismo formato que [03_que_pasa_por_detras.md](03_que_pasa_por_detras.md): complementa
[04_formalizacion.md](04_formalizacion.md) con lo que pasa en el servidor y en la base,
verificado leyendo el código real (solo lectura, sin cambiar nada). Cubre los 10 pasos
recorridos hoy: panel → bandeja → condiciones → confirmar → documentos generados → ficha
completa → ver contrato/pagaré/plan de pagos → cargar los 3 firmados. Lo que no pude confirmar
con una lectura directa queda marcado **"a confirmar"**.

## 1. Panel de la oficial después de la aprobación (RF30)

- **Pantalla/URL**: `GET /panel`.
- **Servlet/servicio**: `ControladorPanelServlet` → `ServicioPanel.calcularIndicadores`
  (`perfil = OFICIAL_CREDITO`).
- **Lee**: lo común a los 3 perfiles (`pagos`, `prestamos` en mora, `numeradores` +
  `parametros_sistema` para el aviso de timbrado — ver
  [03_que_pasa_por_detras.md](03_que_pasa_por_detras.md) §1 para el detalle completo, es el
  mismo método) + el bloque de oficial: `solicitudes_credito`
  (`SolicitudDAO.listarPorOficial`, cuenta solo `BORRADOR`/`PENDIENTE_APROBACION` para "Mis
  solicitudes en curso") + `gestiones_cobranza` (promesas de hoy, oficial/gerente/administrador).
- **Escribe**: condicional, mismo mecanismo de recálculo diario de mora ya documentado en
  `03_que_pasa_por_detras.md` §1 (idempotente, una sola vez por día, para cualquier usuario).
- **Cambios de estado**: ninguno propio de este paso.
- **Reglas/parámetros**: ninguno específico de formalización.
- **Numeración/Archivos**: ninguno.
- **Auditoría**: ninguna propia de "ver el panel" (solo la condicional del recálculo de mora).
- **Nota**: "Mis solicitudes en curso: 0" porque `SOL-2026-0001` ya es `APROBADA` (ese contador
  no la incluye) — no hay ningún aviso de que la aprobación ya pasó.

## 2. Pantalla /formalizacion (RF11)

- **Pantalla/URL**: `GET /formalizacion`.
- **Servlet/servicio**: `ControladorFormalizacionServlet.mostrarLista` →
  `ServicioFormalizacion.listarParaFormalizar` (sección 1),
  `ServicioFormalizacion.listarPendientesDesembolso` (sección 2),
  `ServicioFormalizacion.listarFinalizadosConCierrePendiente` (sección 3).
- **Lee**: `solicitudes_credito` (`SolicitudDAO.listarAprobadasSinFormalizar` —
  `estado_solicitud = 'APROBADA' AND NOT formalizada`, sección 1), `prestamos` (secciones 2 y
  3), `personas` (columna Cliente de cada sección). El buscador (`ServicioSolicitudes.
  filtrarPorTexto`) filtra las 3 secciones a la vez, en memoria, sin SQL `WHERE` — mismo
  mecanismo que la bandeja de Aprobación (código/cédula/nombre, normalizado).
- **Escribe**: nada.
- **Permiso**: `GENERAR_DOCUMENTOS_FORMALIZACION` (ruta `/formalizacion`) — `TOTAL` para oficial
  y gerente, `NINGUNO` para cajero (ver matriz al final).
- **Auditoría**: ninguna.
- **Numeración/Archivos**: ninguno.

## 3. Condiciones aprobadas y generación de documentos — `/formalizacion/nueva/{id}` (RF11)

- **Pantalla/URL**: `GET /formalizacion/nueva/{idSolicitud}`.
- **Servlet/servicio**: `ControladorFormalizacionServlet.mostrarNueva` →
  `ServicioFormalizacion.proponerPrimerVencimiento` (hoy + 1 mes, pura),
  `calcularRangoPrimerVencimiento` (rango 20-45 días), `calcularResumenParaMostrar` (cuota más
  alta/total/CFT/afectación), `feriadoDAO.listarActivos` (para el aviso en vivo de día no
  hábil).
- **Lee**: `solicitudes_credito`, `personas`/`personas_laborales`/`personas_domicilio` (afectación
  actual), `parametros_sistema` (`credito.limite_afectacion`,
  `formalizacion.dias_min_primer_vencimiento`/`dias_max_primer_vencimiento`), `feriados`.
- **Escribe**: nada — pantalla de solo lectura antes de confirmar.
- **Reglas/parámetros que controla**: rango del primer vencimiento (20-45 días desde hoy);
  afectación actual recalculada con los datos ACTUALES del cliente (segundo control de RF11,
  independiente de la afectación congelada al aprobar — ver 04_formalizacion.md Paso 3).
- **Numeración/Archivos**: ninguno todavía (el número de operación se genera recién al
  confirmar, Paso 5).

## 4. Confirmar "Generar documentos" (RF12)

- **Pantalla/URL**: `POST /formalizacion/nueva/{idSolicitud}/generar` (desde el mismo
  formulario de `nueva.jsp`, Paso 3).
- **Servlet/servicio**: `ControladorFormalizacionServlet.procesarGenerar` →
  `ServicioFormalizacion.generarDocumentos`.
- **Mecanismo del cuadro de confirmación**: genérico de todo el sistema
  (`WEB-INF/vistas/plantilla/pie.jsp`, SweetAlert2 sobre `form[data-confirmar]`) — no hay
  ningún `.js` propio de esta pantalla, ver el detalle completo en `04_formalizacion.md` Paso 4.
- **Lee/Escribe/Auditoría**: ver el Paso 5 — es la misma llamada a `generarDocumentos`, el
  efecto ocurre de una sola vez al confirmar.

## 5. Documentos generados: préstamo nuevo (RF11-RF13)

- **Pantalla/URL de resultado**: redirige a `GET /formalizacion/{idPrestamo}` (Paso 6).
- **Servicio**: `ServicioFormalizacion.generarDocumentos(idSolicitud, primerVencimiento,
  idUsuario, puedeResolver)`, una sola transacción.
- **Lee**: `solicitudes_credito` (estado `APROBADA`, revalida que no esté ya formalizada vía
  `prestamoDAO.obtenerPorSolicitud`), `limites_tasa_usura` (tope vigente), `parametros_sistema`
  (`formalizacion.dias_min/max_primer_vencimiento`, `credito.factor_moratorio`,
  `credito.porcentaje_punitorio`, `credito.limite_afectacion`), `feriados`,
  `personas_laborales` (afectación actual).
- **Escribe**: `numeradores` (clave `OPERACION`, `FOR UPDATE`); `INSERT prestamos`
  (`estado_prestamo = PENDIENTE_DESEMBOLSO` desde el inicio, `identificador_operacion` =
  `numero_contrato` = `numero_pagare`); `INSERT planes_pago` (versión 1, `ORIGINAL`, vigente);
  `INSERT cuotas` (las 12 filas); `UPDATE solicitudes_credito`
  (`SolicitudDAO.marcarFormalizada` — pone `formalizada = TRUE`; **`estado_solicitud` NO
  cambia**, sigue `APROBADA`); `INSERT archivos_binarios` + `INSERT documentos_expediente` × 3
  (contrato, pagaré, plan de pagos — `origen_archivo = 'JASPER'`, versión 1, `VIGENTE`).
- **Cambios de estado**: solicitud `APROBADA` (sin cambiar) + `formalizada: FALSE → TRUE`;
  préstamo nace directo en `PENDIENTE_DESEMBOLSO` (sin estado intermedio en el `CHECK`).
- **Reglas/parámetros que controla**: tope de tasa vigente contra la TEA aprobada; rango de 20
  a 45 días (fecha elegida Y fecha ya corrida a día hábil); afectación actual ≤
  `credito.limite_afectacion`, con los datos ACTUALES del cliente.
- **Numeración**: `generarIdentificadorOperacion` → `CC-{año}-{secuencia de 5 dígitos}` (clave
  `OPERACION`).
- **Archivos**: 3 PDF nuevos, como `BYTEA` en `archivos_binarios` (nunca en disco) —
  `generarSiFalta` para cada uno de `CONTRATO_GENERADO`/`PAGARE_GENERADO`/`PLAN_PAGOS_GENERADO`.
- **Auditoría — 4 filas, no 1**: `GENERAR_DOCUMENTOS_FORMALIZACION` ("Generar documentos de
  formalización"), entidad PRESTAMO, con el detalle de la fecha (elegida/efectiva/motivo del
  corrimiento) y la cuota/CFT; más 3× `GENERAR_PDF_FORMALIZACION` ("Re-emitir PDF de
  formalización"), entidad DOCUMENTO_EXPEDIENTE, una por cada PDF generado
  (`guardarPdfGenerado`).
- **Mensaje de éxito exacto** (verificado en `ControladorFormalizacionServlet.procesarGenerar`):
  **"Documentos generados. Ahora cargue los firmados en el checklist de esta ficha."**
- **Doble envío**: protegido por la restricción `UNIQUE` de `prestamos.id_solicitud` — un
  segundo `POST` (doble clic, F5) antes de que el primero comitee cae en `SQLState 23505`,
  traducido al mismo mensaje de negocio ("Esta solicitud ya fue formalizada (CC-...)"), nunca
  un error genérico — **a confirmar en pantalla**, no se probó hoy (ver "Completitud del tramo
  3").

## 6. Recorrido completo de la ficha `/formalizacion/{idPrestamo}` (RF11-RF13)

- **Pantalla/URL**: `GET /formalizacion/{idPrestamo}`.
- **Servlet/servicio**: `ControladorFormalizacionServlet.mostrarFicha`.
- **Lee**: `prestamos`, `solicitudes_credito`, `personas`, `cuotas`/`planes_pago`
  (`listarCuotasVigentes`), `documentos_expediente` (checklist + los 3 PDF generados,
  `obtenerChecklist`), `parametros_sistema` (los mismos de la Sección 3, para la tarjeta
  "Regenerar documentos"), `feriados`, `limites_tasa_usura`.
- **Escribe**: nada — ficha de solo lectura; las tarjetas "Re-emitir los PDF"/"Regenerar
  documentos" solo muestran formularios, no se ejecutó ninguno hoy.
- **Reglas/parámetros que controla la tarjeta "Checklist antes del desembolso"**:
  `ServicioFormalizacion.esChecklistCompleto(obtenerChecklist(...))` — exige un documento
  vigente para los 3 tipos con `obligatorio_desembolso = TRUE`
  (`CONTRATO_FIRMADO`/`PAGARE_FIRMADO`/`PLAN_PAGOS_FIRMADO`) — es el MISMO método que usa
  `listarListosParaDesembolsar` para la bandeja de la cajera (confirmado en el Paso 10/C1 de
  tramo 2).
- **Numeración/Archivos**: ninguno nuevo (solo lectura de lo ya generado en la Sección 5).
- **Auditoría**: ninguna por ver la ficha.

## 7. Ver el contrato en PDF (RF12)

- **Pantalla/URL**: `GET /documentos/{idArchivo}` (ruta genérica de documentos, no propia de
  Formalización).
- **Plantilla**: `src/main/resources/reportes/contrato.jrxml` — banda `detail` por fila de un
  datasource de cláusulas armado en Java (sin ningún texto de cláusula en el `.jrxml`).
- **Servicio**: `ServicioFormalizacion.generarPdfContrato` → `construirParametrosReporte`
  (empresa, cliente, montos, fechas) + `construirClausulasContrato` (las 10 cláusulas,
  hardcoded, interpolando esos parámetros + `DESTINO_CREDITO`/`CUOTAS_ALERTA_GARANTE`/
  `PORCENTAJE_PUNITORIO`).
- **Lee**: `prestamos`, `solicitudes_credito` (destino), `personas`/`personas_domicilio`,
  `parametros_sistema` (`empresa.*` vigentes a la fecha de formalización,
  `empresa.representante_legal`, `credito.cuotas_alerta_garante`,
  `credito.porcentaje_punitorio`).
- **Escribe**: nada — el PDF ya se generó en la Sección 5; este paso solo lo DESCARGA/MUESTRA
  (`ArchivoBinarioDAO.obtenerContenido`), sin volver a generarlo ni auditar nada nuevo.
- **Hallazgo propio de este documento**: C3 (cláusula SEGUNDA contradice al plan sobre el día
  de las cuotas siguientes) — ver `04_formalizacion.md` Paso 7.

## 8. Ver el pagaré en PDF (RF12)

- **Pantalla/URL**: `GET /documentos/{idArchivo}`.
- **Plantilla**: `pagare.jrxml` — una sola banda de texto fijo, `JREmptyDataSource(1)` (sin
  datasource de filas).
- **Servicio**: `ServicioFormalizacion.generarPdfPagare` → la MISMA `construirParametrosReporte`
  que el contrato (una sola fuente de parámetros para los 3 documentos).
- **Lee/Escribe**: igual que la Sección 7 — solo lectura/descarga, sin generar ni auditar de
  nuevo.
- **Sin hallazgos** (ver `04_formalizacion.md` Paso 8).

## 9. Ver el plan de pagos en PDF (RF12)

- **Pantalla/URL**: `GET /documentos/{idArchivo}`.
- **Plantilla**: `plan_pagos.jrxml` — datasource de filas (`CuotaReporte`, una por cuota,
  `JRBeanCollectionDataSource`).
- **Servicio**: `ServicioFormalizacion.generarPdfPlanDePagos` → la MISMA
  `construirParametrosReporte` + `listarCuotasVigentes` (el mismo método que ya usa la ficha,
  Sección 6 — una sola fuente para pantalla y PDF).
- **Lee/Escribe**: igual que las Secciones 7 y 8 — solo lectura/descarga.
- **Confirma**: las cifras coinciden con la ficha y las cuotas siguientes quedan ancladas al
  día original (21, no 23) — termina de confirmar que C3 (Sección 7) es solo del texto del
  contrato, no del plan real (ver `04_formalizacion.md` Paso 9).

## 10. Cargar los 3 firmados (RF13)

- **Pantalla/URL**: `POST /documentos/cargar` (ruta genérica, con `idPrestamo` en vez de
  `idPersona` — mismo endpoint multipart/CSRF que el expediente del cliente).
- **Servlet/servicio**: `ControladorDocumentosServlet` (método `procesarCargarDocumento`,
  rama `idPrestamo`) → `ServicioDocumentos.cargarDocumentoPrestamo`.
- **Lee**: `documentos_expediente` (`obtenerVigentePorPrestamoYTipo`, para saber si ya hay un
  vigente de ese tipo — decide `CARGAR_DOCUMENTO_FORMALIZACION` vs.
  `NUEVA_VERSION_DOCUMENTO_FORMALIZACION`), `parametros_sistema`
  (`archivos.tamano_maximo_mb` = 5 MB).
- **Escribe**: `INSERT archivos_binarios` (`origen_archivo = 'CARGADO'`, hash SHA-256); `INSERT
  documentos_expediente` (`id_prestamo`, `estado_documento = 'VIGENTE'`, versión 1 o
  `vigenteAnterior + 1`); si había un vigente anterior, `UPDATE` lo marca `REEMPLAZADO` (nunca
  se borra).
- **Reglas/parámetros que controla "checklist"**: cada carga vuelve a calcular
  `itemsFaltantesChecklistPrestamo(idPrestamo)` (que internamente llama a
  `esChecklistCompleto`/`obtenerChecklist`, Sección 6) para armar el mensaje flash exacto.
- **Validaciones de archivo**: `ServicioDocumentos.validarArchivo` — extensión declarada
  PDF/JPG/PNG, firma real de los primeros bytes debe coincidir, tamaño ≤ 5 MB
  (`archivos.tamano_maximo_mb`).
- **Mensajes de éxito exactos** (verificados en `ControladorDocumentosServlet`):
  - Si falta algo: **"Documento cargado. Faltan: [lista], ..."**
  - Si queda completo: **"Documento cargado. Checklist completo: listo para desembolso."**
- **Numeración**: ninguna (los firmados no generan ningún número nuevo, reutilizan el tipo de
  documento ya existente).
- **Auditoría**: `CARGAR_DOCUMENTO_FORMALIZACION` ("Cargar documento del cliente") la primera
  vez que se carga ese tipo; `NUEVA_VERSION_DOCUMENTO_FORMALIZACION` ("Reemplazar documento del
  cliente") si ya había un vigente — entidad DOCUMENTO_EXPEDIENTE en los dos casos.
- **Qué habilita**: completar el checklist (los 3 vigentes) es lo que hace que
  `listarListosParaDesembolsar` (Sección 6) incluya este préstamo en la bandeja de la cajera —
  confirmado en el código; **a confirmar en pantalla** en el tramo 4.

## Matriz de permisos relevante para este tramo

| Permiso | Ruta/acción | Gerente | Oficial | Cajero |
|---|---|---|---|---|
| `GENERAR_DOCUMENTOS_FORMALIZACION` | `/formalizacion` (lista, nueva, ficha, generar, regenerar, re-emitir) | TOTAL | TOTAL | NINGUNO |
| `CARGAR_DOCUMENTOS_CONSULTA_BURO` | `/documentos/cargar` (carga de firmados) | TOTAL | TOTAL | NINGUNO |
| `ANULAR_PRESTAMO_PRE_DESEMBOLSO` | `/formalizacion/{id}/anular` — chequeo de perfil explícito en el controlador, no por `FiltroAutorizacion` | TOTAL (solo gerente/administrador, validado en código) | NINGUNO | NINGUNO |

## A confirmar

- Si, en la prueba de hoy, el recálculo diario de mora (Sección 1) ya había corrido antes de
  que la oficial volviera a abrir el panel (mismo punto ya marcado "a confirmar" en
  `03_que_pasa_por_detras.md` §1) — no lo determiné sin consultar la base.
- El doble envío de "Generar documentos" (protección `SQLState 23505`, Sección 5) está
  verificado en el código pero no se probó en pantalla hoy.
- Si el número de "listos para desembolsar" del panel de la cajera sube a 1 justo al completar
  el checklist de este préstamo (Sección 10) — no se verificó en pantalla, sin loguearse como
  cajera en este tramo.
