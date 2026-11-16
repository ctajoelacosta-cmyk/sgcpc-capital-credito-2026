# Escena 01 — Llega el cliente

**Recorrido probado en pantalla por Joel el 21/10/2026, rol oficial de crédito, base limpia
(`sgcpc_db_pruebas` recién recreada, sin humo).** Reemplaza el contenido migrado de
`docs/funcionamiento/FLUJO_OPERATIVO.md` (recorrido del 17/10/2026) por lo visto hoy.

**Cliente de ejemplo (ficticio, no es el nombre ni la cédula reales cargados en la prueba de
hoy)**: **Marta Ovelar (C.I. 1.000.001)**, soltera, asalariada, con comprobante de ingreso.

**Qué pasa por detrás** (tablas, parámetros, auditoría, archivos y numeraciones de cada paso,
verificado contra el código): [01-02_que_pasa_por_detras.md](01-02_que_pasa_por_detras.md).

**Contexto**: la entrevista arrancó con inicio de sesión y la primera vista del panel (Escena
00, todavía sin recorrer formalmente). Hallazgo observado hoy sobre esa pantalla, anotado acá
para no perderlo y a trasladar cuando se recorra la Escena 00: el panel no tiene accesos
directos a "Cotizar" ni a "+ Nuevo cliente", y "Mis solicitudes" podría ir más arriba.

> **CONSOLIDADO (22/10/2026, Joel): el cotizador es el primer paso del oficial de crédito al
> atender a un cliente. Funcionamiento verificado en el recorrido del 21/10 y repetido el
> 22/10 con un segundo cliente de práctica.**

### Quién lo usa

- Permiso `COTIZAR` = **TOTAL** para gerente, oficial y cajera (administrador, TOTAL en todo) —
  ver [RF38_perfiles.md](../funcionamiento/RF38_perfiles.md) §2.2 (menú por perfil) y su tabla
  de permisos.
- **Motivo**: es una simulación que no guarda ningún registro; lo que está restringido es lo que
  sigue en el circuito (la solicitud, solo oficial; la aprobación, solo gerente; el desembolso,
  solo cajera) — el cotizador en sí no compromete a nadie.
- **Uso práctico**: oficial (herramienta principal, antes de registrar al cliente); gerente
  (consultas); cajera (consultas en ventanilla).

## Paso 1 — Cotizador (RF01)

- **Usuario/rol**: Oficial de crédito.
- **Pantalla y ruta**: `GET`/`POST /cotizador`.
- **Qué hace el usuario**: antes de registrar al cliente, simula el préstamo con monto
  3.000.000, plazo 12 meses y una fecha propuesta del primer vencimiento. Nombre y C.I. del
  interesado son opcionales (solo para el PDF).
- **Datos que pide** (obligatorios y validaciones): monto, plazo, fecha del primer vencimiento;
  nombre y C.I. opcionales. Ningún otro dato del cliente.
- **Qué responde el sistema**: cuota con IVA 289.919, total a pagar 3.447.270, TEA/TNA/CFT,
  tabla "Comparar plazos" (6/12/18/24), plan de pagos completo, aviso de "cotización
  informativa". Botones "Nueva cotización" / "Imprimir" (PDF con el mismo resultado).
- **Regla de negocio**: ver [RF01_cotizador.md](../funcionamiento/RF01_cotizador.md) §1-§2.
- **Qué cambia en la base**: nada — verificado en `ServicioCotizacion.cotizar` (método puro, sin
  ninguna `Connection` ni ningún `INSERT`/`UPDATE`): "nunca se guarda nada", cumple literalmente
  el texto del anteproyecto. `GET`/`POST /cotizador` (ver el resultado en pantalla) tampoco
  escribe nada en `auditoria_operaciones` — RF01 es la única pantalla operativa sin evento
  propio para la simulación en sí.
- **Qué registra la auditoría**: nada, mientras solo se mire el resultado en pantalla. **Matiz
  verificado en el código**: el botón **"Imprimir"** (`POST /cotizador/informe`) sí registra un
  evento — `EMITIR_COTIZACION_PDF` (entidad `COTIZACION`, sin fila real detrás, `id_entidad = 0`)
  — porque ahí se emite un PDF con el nombre de un interesado, no por guardar la cotización
  (que sigue sin persistir en ninguna tabla, ni siquiera al imprimir).
- **Qué sigue**: el oficial pasa a registrar al cliente (Paso 2) — el cotizador no entrega
  ningún dato a esa pantalla, se vuelve a escribir todo.
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena (ítems de Cotizador).

> **CONSOLIDADO (22/10/2026, Joel): la precalificación es el segundo paso del oficial, dentro de
> Clientes → Nuevo cliente. Verificado en el recorrido del 21/10 y repetido el 22/10 con un
> segundo cliente de práctica.**

### Verificado en el código (22/10/2026)

- **Quién puede entrar a `/clientes` y `/clientes/nuevo`**: una sola ruta protegida —
  `FiltroAutorizacion.RUTAS_PROTEGIDAS.put("/clientes", Permiso.REGISTRAR_EDITAR_CLIENTE)` (cubre
  también `/clientes/nuevo`, un sub-path de la misma ruta). Matriz
  (`ServicioAutorizacion.construirMatriz`, confirmada en
  [RF38_perfiles.md](../funcionamiento/RF38_perfiles.md) §2.2): **gerente TOTAL**, **oficial
  TOTAL**, **cajera CONSULTA** (entra y ve, pero el servidor rechaza cualquier `POST`),
  **administrador TOTAL** (en todo, D7).
- **De dónde salen las 4 reglas de la precalificación**: 3 son parámetros, 1 está fija en el
  código:
  - Edad (22-71 años): `credito.edad_minima`/`credito.edad_maxima`.
  - Departamento habilitado (Asunción/Central): `credito.departamentos_habilitados` (lista
    separada por comas).
  - Situación laboral con ingreso fijo: `credito.situaciones_laborales_admitidas` (lista
    separada por comas).
  - Comprobante de ingreso obligatorio: **no es un parámetro** — `ServicioClientes.
    motivoSinComprobanteSiAplica`/`MOTIVO_SIN_COMPROBANTE`, una regla fija en el código ("Sin
    comprobante de ingresos no puede solicitar un crédito (política vigente)."); el propio
    comentario del código explica que antes este texto estaba duplicado en dos lugares y se
    desincronizó una vez — ahora es una sola fuente.
  - Las 4 se evalúan juntas en `ServicioClientes.evaluarPrecalificacion` (`GET
    /clientes/precalificar`), reutilizando el mismo método base (`evaluarPoliticaCredito`) que
    usa `validarPoliticaCreditoAlta` al registrar.
- **¿Solo en el navegador, o también en el servidor?** También en el servidor.
  `ServicioClientes.alta` llama a `validarPoliticaCreditoAlta` **antes** de cualquier `INSERT` —
  si alguien forzara un `POST /clientes/nuevo` saltándose la pantalla de precalificación, con una
  edad, zona, situación laboral o falta de comprobante que no cumplan, el servidor rechaza igual
  (mismo mensaje puntual, nunca uno genérico), sin escribir nada.
- **¿La precalificación guarda algo antes de registrar al cliente?** No — `evaluarPrecalificacion`
  es de solo lectura (lee parámetros, nunca escribe); la persona recién se inserta al completar
  todo el formulario y tocar "Registrar cliente" (`ServicioClientes.alta`).
- **¿De dónde sale "Documentos a presentar"?** De código, no de una tabla —
  `ServicioClientes.documentosSugeridosPorSituacionLaboral` arma la lista con texto fijo según la
  situación laboral (cédula, comprobante de domicilio y el comprobante de ingreso que
  corresponda — `descripcionComprobanteIngresoSegunSituacionLaboral`); no lee `tipos_documento`
  ni ninguna otra tabla. **"Imprimir requisitos" no deja auditoría** — revisado
  `ServicioClientes.generarInformeRequisitosPdf` de punta a punta: ningún
  `servicioAuditoria.registrar` (a diferencia del PDF del cotizador, que sí audita
  `EMITIR_COTIZACION_PDF` — acá no hay ese mismo tratamiento).
- **Resueltos los dos puntos "a confirmar" de
  [01-02_que_pasa_por_detras.md](01-02_que_pasa_por_detras.md)**:
  - `tipos_documento` **sí tiene** una columna booleana (`obligatorio_desembolso`,
    `bd/01_esquema.sql`) — pero es para el checklist del desembolso (RF13/RF16: contrato, pagaré
    y plan de pagos firmados), no para el Expediente de alta del cliente. La lista de
    obligatorios del Expediente sigue siendo solo el `Set` de Java ya citado arriba
    (`CODIGOS_EXPEDIENTE_OBLIGATORIOS`), sin relación con esa columna.
  - `GeneradorInformesPdf.parametrosBase` lee `empresa.razon_social`, `empresa.ruc`,
    `empresa.domicilio`, `empresa.telefono`, `empresa.whatsapp` (más `USUARIO_EMISOR` y
    `FECHA_EMISION`, que no son parámetros) — tanto el PDF de cotización como el de requisitos
    vuelven a leer `empresa.telefono`/`empresa.whatsapp` por su cuenta encima de lo que ya trae
    `parametrosBase`, una lectura duplicada sin efecto (mismo valor, dos veces).

**Nota**: el papel de requisitos ya pide el comprobante de domicilio como obligatorio
(coherente con la decisión del 21/10); la exigencia real dentro del Expediente sigue pendiente
para la fase 2 (ver Hallazgos, abajo).

## Paso 2 — Alta del cliente: precalificación y datos (RF02)

- **Usuario/rol**: Oficial de crédito.
- **Pantalla y ruta**: `GET`/`POST /clientes/nuevo`.
- **Qué hace el usuario**: completa Precalificación (fecha de nacimiento, departamento,
  situación laboral, ¿presenta comprobante?) arriba de todo; el sistema confirma "Califica" al
  instante con la edad y desbloquea el resto del formulario. Luego completa datos personales,
  domicilio, contacto, trabajo e ingresos (empleador si es asalariada), 2 referencias (sin
  cónyuge, por ser soltera), y "Registrar cliente".
- **Datos que pide** (obligatorios y validaciones): cédula (no repetida), vencimiento de
  cédula, nombres, apellidos, estado civil; domicilio (ciudad filtrada por departamento, barrio,
  dirección, tipo de vivienda, antigüedad); teléfono; datos del empleador si asalariada;
  ingreso neto comprobado y cuota de otras deudas (en letras); 2 referencias (nombre y
  teléfono).
- **Qué responde el sistema**: "Califica: puede continuar con el registro" al completar la
  precalificación, con "Imprimir requisitos" habilitado (PDF según situación laboral). Al
  registrar: "… se registró correctamente. Siguiente paso: cargar sus documentos en
  Expediente."; queda ✓ en la pestaña ① Datos.
- **Regla de negocio**: ver [RF02_clientes.md](../funcionamiento/RF02_clientes.md) §1-§2.
- **Qué cambia en la base**: `INSERT` en `personas` (+ `personas_domicilio`, `personas_laborales`
  si corresponde, `personas_referencias`) — queda `ACTIVA`.
- **Qué registra la auditoría**: `ALTA_CLIENTE`, entidad PERSONA.
- **Qué sigue**: "Siguiente paso: Expediente" (Paso 3).
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena (ítems de Precalificación/
  papel de requisitos y de Datos personales).

> **CONSOLIDADO (22/10/2026, Joel): el registro del cliente es el tercer paso del oficial, al
> final de la misma pantalla de Precalificación/Datos. Verificado en el recorrido del 21/10 y
> repetido el 22/10 con un segundo cliente de práctica.**

### Verificado en el código (22/10/2026)

- **Qué escribe "Registrar cliente"**: una sola transacción, `ServicioClientes.alta` — `INSERT`
  en `personas` (nueva fila, `ACTIVA`), `personaReferenciaDAO.reemplazarTodas` (las 2
  referencias), `personaConyugeDAO.guardar` (nada si es soltera/o, confirmado con `SELECT`: el
  segundo cliente de práctica tampoco tiene cónyuge guardado), `personaDomicilioDAO.guardar`,
  `personaLaboralDAO.guardar`. Auditoría: `ALTA_CLIENTE`, entidad `PERSONA`, detalle `Registró
  la ficha de "[nombre]" (C.I. [cédula]).` — confirmado tal cual con `SELECT` sobre
  `auditoria_operaciones` en `sgcpc_db_pruebas`.
- **¿Impide la cédula repetida?** Sí, en el servidor, antes de escribir nada —
  `personaDAO.buscarPorCedula(conexion, cedula)` se consulta dentro de la misma transacción de
  `alta`; si ya existe, `NegocioException`: **"Ya existe una persona registrada con la cédula
  "[cédula]"."** (mismo mensaje, con "otra", en la edición).
- **Campos del empleador según la situación laboral**: en pantalla, un bloque de JS
  muestra/oculta esos campos según lo que se elija en "Situación laboral" (solo informativo). El
  servidor los exige de verdad, independiente de lo que se vea en pantalla —
  `ServicioClientes.validarLaboral`: si la situación está en
  `SITUACIONES_LABORALES_CON_EMPLEADOR` (`ASALARIADO_SECTOR_PRIVADO`, `FUNCIONARIO_PUBLICO`),
  rechaza con **"Complete los datos del empleador (nombre, dirección, teléfono, cargo y
  antigüedad)."** si falta alguno; para cualquier otra situación (comerciante, jubilado, etc.)
  esos campos ni se piden.
- **Mensaje de éxito y redirección**: confirmado en `ControladorClientesServlet` (rama
  `/nuevo`) — **"[Nombre] (C.I. [cédula]) se registró correctamente. Siguiente paso: cargar sus
  documentos en Expediente."**, y `response.sendRedirect(.../clientes/" + idNuevo)` — la
  pantalla pasa directo a la ficha del cliente recién creado, nunca se queda en el formulario de
  alta.
- **"Guardar cambios" (edición), auditoría y re-validación**: `ServicioClientes.actualizar`
  audita `ACTUALIZAR_CLIENTE` con el detalle **"Actualizó la ficha de "[nombre]"."**, y agrega
  **", Campos modificados: ..."** solo si de verdad cambió algo (`camposModificados`, comparación
  campo por campo — confirmado con `SELECT`: una edición real sobre el segundo cliente de
  práctica, sin cambios de datos reales, quedó auditada sin esa lista, tal como predice el
  código). Sobre la pregunta de si vuelve a validar la precalificación: **dos mecanismos
  distintos, verificados los dos**:
  1. **Bloqueo duro, antes de guardar nada** (`bloquearSiCambianDatosDeEvaluacion`): solo si el
     cliente tiene una evaluación en curso (una solicitud `PENDIENTE_APROBACION`/`APROBADA` sin
     formalizar, o un préstamo `PENDIENTE_DESEMBOLSO`) **y** se intenta cambiar alguno de los 7
     datos de evaluación (incluida la fecha de nacimiento o el departamento) — ahí sí rechaza el
     guardado completo.
  2. **Aviso, después de guardar, sin bloquear nunca** (`listarMotivosNoCalifica`, llamado al
     final de `actualizar`, ya dentro de la misma transacción que recién confirmó): si NO hay
     ninguna evaluación en curso, el cambio se guarda siempre — aunque la nueva fecha de
     nacimiento o el nuevo departamento dejen a la persona fuera de la política vigente — y el
     método devuelve la lista de motivos para el aviso amarillo "se guardó, pero atención",
     nunca para impedir el guardado.
- **¿Limpia espacios o puntuación sobrante?** Solo espacios — verificado en
  `NormalizadorTexto.normalizarTextoLibre` (usada para barrio y referencia de domicilio en
  `validarDomicilio`): recorta los espacios de los extremos y colapsa los espacios internos
  repetidos a uno solo, pero **no toca ninguna puntuación**. Confirmado con `SELECT` sobre
  `personas_domicilio` en `sgcpc_db_pruebas`: hay un barrio real guardado con una coma al final,
  tal cual se escribió — ni el navegador ni el servidor la recortan.

## Paso 3 — Expediente de documentos (RF03)

**Registro de lo observado en uso real (22/10/2026), con un segundo cliente de práctica,
además del recorrido original del 21/10.**

- **Usuario/rol**: Oficial de crédito.
- **Pantalla y ruta**: `/clientes/{id}#tabDocumentos` (pestaña ② Expediente), llegada con
  "Siguiente: ② Expediente →" desde ① Datos; generar/cargar la autorización vía
  `POST /clientes/{id}/autorizacion-buro` y `/documentos/cargar-autorizacion-firmada`.
- **Qué responde el sistema (pantalla completa)**: título **"Expediente: 0 de 3 documentos
  obligatorios"** con una barra de avance, y debajo **"Documentos para: [situación laboral]"**.
  Lista de 3 filas (más una intermedia):
  1. **Cédula de identidad** (obligatorio) — "Ambos lados, vigente" — estado **Falta** — botón
     **Cargar**.
  2. **Comprobante de ingreso** (obligatorio) — la descripción cambia según la situación laboral
     (ej. "Liquidación de sueldo o extracto de aportes IPS (últimos 3 a 6 meses)." para
     asalariado) — **Falta** — **Cargar**. Debajo, en la misma fila: **Comprobante de
     domicilio** (complementario) — "Factura de ANDE/ESSAP/telefonía (últimos 2 meses)" —
     **"No cargado"** — **Cargar**.
  3. **Autorización de consulta al buró** (obligatorio) — "Firmada por el cliente. Sin ella no
     se puede consultar Informconf." — con 3 subpasos: **1. Generar la autorización** (botón
     "Generar autorización"), **2. El cliente firma la autorización impresa**, **3. Cargar
     autorización firmada**.
  Botón **"Siguiente: ③ Consulta al buró →"**.
- **"Generar autorización"**: cuadro de éxito — **"Autorización N.º AUT-[año]-[NNNNN] generada
  — Siguiente paso: imprímala, que el cliente la firme, y cárguela escaneada."**, con
  **"Abrir para imprimir"** y **"Aceptar"**. La fila 3 pasa a mostrar **"✓ Generada el
  [fecha hora]"** y los botones **"Reimprimir autorización"** / **"Cargar autorización
  firmada"**.
- **El PDF de la autorización** ("autorizacion_buro.jasper"): encabezado de la empresa; título
  **"AUTORIZACIÓN PARA CONSULTA DE INFORMACIÓN CREDITICIA"**; N.º de autorización; lugar/fecha/
  hora ("Asunción, [fecha hora]"); **"Vigente hasta [fecha]"** (30 días desde la emisión); datos
  del cliente (nombre, C.I., domicilio, teléfono); el texto legal (autoriza consultar y/o
  reportar sus datos conforme a la **Ley N.º 6534/2020** "De Protección de Datos Personales
  Crediticios", con la única finalidad de evaluar el crédito solicitado, y declara conocer sus
  derechos de acceso, rectificación y actualización); firma del cliente con nombre y C.I.;
  recuadro **"USO INTERNO"** (N.º, "Generada por [usuario]", "Recibido y verificado por: ___
  Firma: ___"); pie "Emitido por" y "Página 1 de 1".
- **"Cargar autorización firmada"**: cuadro de éxito — **"Autorización cargada y consentimiento
  registrado. Faltan: Cédula de identidad, Comprobante de ingreso, Comprobante de
  domicilio."** — y el sistema lleva **automáticamente** a la pestaña ③ Consulta al buró, aunque
  el expediente sigue incompleto (solo la autorización quedó cargada; cédula, comprobante de
  ingreso y comprobante de domicilio siguen sin cargar) — ver C14 y B27 en Hallazgos.
- **Regla de negocio**: ver [RF03_expediente.md](../funcionamiento/RF03_expediente.md) §1-§2 y
  §10 (comprobante de domicilio complementario, decisión del 19/10/2026 — **superada el
  28/10/2026: pasó a obligatorio**, ver §7 de ese mismo documento) y
  [RF04_buro.md](../funcionamiento/RF04_buro.md) (autorización, misma pestaña). **Nota
  (21/10/2026)**: el comprobante de domicilio sigue complementario en pantalla — la decisión de
  Joel de que pase a ser obligatorio está **pendiente de implementar** (fase 2).
- **Qué cambia en la base**: al generar, nada en una tabla propia — solo `numeradores` (clave
  `AUTORIZACION`) y el número queda guardado como `valor_nuevo` de la fila de auditoría, sin
  tabla dedicada para "autorizaciones". Al cargar la firmada: `archivos_binarios` +
  `documentos_expediente` (el escaneo) y, en una transacción separada,
  `consentimientos_crediticios` (el consentimiento).
- **Qué registra la auditoría**: `GENERAR_AUTORIZACION_BURO` (PERSONA, con el número como
  `valor_nuevo`); `CARGAR_DOCUMENTO` (DOCUMENTO_EXPEDIENTE) por cada carga (autorización
  firmada, cédula, comprobante de ingreso); `REGISTRAR_CONSENTIMIENTO`
  (CONSENTIMIENTO_CREDITICIO) al cargar la autorización firmada. Confirmado con `SELECT` sobre
  `auditoria_operaciones` en `sgcpc_db_pruebas`, con el segundo cliente de práctica: el
  `GENERAR_AUTORIZACION_BURO` y, 6 minutos después en pantalla, el `CARGAR_DOCUMENTO` +
  `REGISTRAR_CONSENTIMIENTO` quedaron con la misma marca de segundo (dos transacciones
  separadas, pero un solo clic del lado del usuario).
- **Qué sigue**: "Siguiente: registrar el resultado del buró" (Paso 4).

### Verificado en el código (22/10/2026)

- **Qué cuenta como "3 documentos obligatorios"**: `ServicioClientes.
  CODIGOS_EXPEDIENTE_OBLIGATORIOS` (cédula, comprobante de ingreso, autorización de buró) — la
  lista por situación laboral (qué comprobante de ingreso corresponde) sale de
  `descripcionComprobanteIngresoSegunSituacionLaboral`, la misma que ya usa "Imprimir
  requisitos" (ver más arriba). El contador y la barra de avance del encabezado
  (`ControladorClientesServlet.mostrarFicha`) filtran explícitamente por
  `ItemExpediente.isObligatorio()` — solo cuentan los 3, nunca el comprobante de domicilio.
- **Numerador, tablas y auditoría de generar/cargar la autorización**: ver arriba ("Qué cambia
  en la base"/"Qué registra la auditoría") — `ServicioConsultaCrediticia.
  generarAutorizacionBuro` (numerador `AUTORIZACION`, formato `AUT-%d-%05d`) y
  `cargarAutorizacionFirmada` (sube el escaneo con `ServicioDocumentos.cargarDocumento` y
  registra el consentimiento con `registrarConsentimiento`, dos transacciones).
  `cargarAutorizacionFirmada` además exige que la última autorización generada sea de **hoy**
  (`autorizacionGeneradaHoy`) — si no, rechaza **antes de subir nada**: **"Esta autorización es
  de otro día. Genere una nueva para que la fecha coincida con la firma."**
- **De dónde salen los 30 días de vigencia y qué pasa cuando vence**: parámetro
  `buro.dias_vigencia_consentimiento` (D-38), usado en
  `ServicioConsultaCrediticia.calcularFechaVencimiento` (`fecha_consentimiento + días`). Cuando
  vence, **sí bloquea, pero solo en dos momentos puntuales** —
  `validarConsultaPermitida` lanza **"La autorización de consulta venció el [fecha]. Cargue una
  autorización nueva firmada por el cliente."** desde `registrarConsulta` (al registrar el
  resultado del buró) y desde `obtenerParaVincularSolicitud` (al enviar la solicitud a
  aprobación) — nunca al solo mirar la pestaña ③, ni al generar una autorización nueva.
- **Por qué salta automáticamente a `#tabBuro`**: `ControladorDocumentosServlet.
  procesarCargarAutorizacionFirmada` siempre redirige a `/clientes/{id}#tabBuro`, **sin
  condición** — a diferencia de la carga genérica de un documento
  (`itemsFaltantesExpediente`, usada en el `doPost` de `/cargar`), que solo salta a `#tabBuro`
  si el expediente quedó completo. Es un salto fijo, no depende de si falta algo más.
- **¿La consulta al buró exige solo la autorización vigente?** No, tres cosas, las tres
  verificadas en `ServicioConsultaCrediticia.registrarConsulta`: autorización vigente
  (`validarConsultaPermitida`), el informe de Informconf ya cargado como evidencia
  (`validarDocumentoResultado`, exige `idDocumentoResultado` no nulo) e indicar si hay mora
  (`validarIndicadorMora`, no puede quedar sin responder). **No exige** cédula, comprobante de
  ingreso ni comprobante de domicilio — esos son del Expediente, un control independiente.
- **¿En qué momento se exige el expediente completo?** **No al crear la solicitud** —
  `ServicioSolicitudes.crear` solo valida la política de crédito (edad/zona/situación
  laboral/comprobante) y que no haya un compromiso abierto, nunca `agregarFaltantesExpediente`.
  **Sí al enviarla a aprobación** — `enviarAAprobacion` llama a `agregarFaltantesExpediente`
  (cédula + comprobante de ingreso, nunca domicilio) y `agregarFaltantesBuro` (autorización
  vigente + consulta con evidencia registrada), juntando TODOS los faltantes en un solo
  mensaje: **"Faltan completar estos puntos antes de enviar a aprobación:"** + una viñeta por
  cada uno. **Al aprobar** (`ServicioAprobacionCredito.aprobar`) — verificado de punta a
  punta — **no se vuelve a chequear el expediente ni la autorización**: solo revalida el tope
  de tasa vigente, que la afectación siga dentro del límite ya congelado, y que el buró no
  muestre mora (RN-18, re-vinculando la consulta más reciente con evidencia).
- **Por qué el cuadro de "Cargar autorización firmada" lista el comprobante de domicilio entre
  los faltantes**: la causa raíz es que **dos métodos distintos usan dos criterios distintos**
  sobre la misma lista de `ItemExpediente`. El contador "N de 3" (arriba) filtra por
  `isObligatorio()`. El mensaje "Faltan: ..." de este cuadro
  (`ControladorDocumentosServlet.itemsFaltantesExpediente`, usado también por la carga
  genérica) recorre **todos** los `ItemExpediente` de `ServicioClientes.
  construirItemsExpediente` y agrega cualquiera con `!isCumplido()` — **sin mirar
  `isObligatorio()`** — así que el comprobante de domicilio entra igual, aunque la ficha lo
  trate como complementario en todos los demás lugares. Ver C14. **(28/10/2026: ya no hay
  contradicción — el domicilio pasó a obligatorio y la ficha también lo exige. Este método sigue
  igual, sin filtrar, y ahora coincide con el resto.)**

### Registro adicional: volver al Expediente desde la solicitud (22/10/2026)

**Registro de lo observado en uso real, con el segundo cliente de práctica, con la solicitud ya
creada (en Borrador) y el buró ya registrado.**

- **Antes de cargar nada**: **"Expediente: 1 de 3 documentos obligatorios"**, barra al **33 %**
  (solo la autorización cuenta como cumplida); la pestaña **④** ya no dice "Solicitud" — pasó a
  mostrar **"SOL-[año]-[número] (Borrador) →"**, y lleva directo a esa solicitud; la fila de la
  autorización muestra **"✓ Generada el [fecha hora]"** y **"✓ Cargada [fecha]"**, con "Ver" y
  "Reemplazar"; el botón de abajo sigue diciendo **"Siguiente: ③ Consulta al buró →"** aunque
  el buró ya está registrado — ver B34.
- **"Cargar" en 1. Cédula de identidad**: cuadro de éxito — **"Documento cargado. Faltan:
  Comprobante de ingreso, Comprobante de domicilio."**, con **"Aceptar"**. La fila de la
  cédula pasa a **"✓ Cargado [fecha]"**, con "Ver" y "Reemplazar"; el contador sube a **"2 de
  3"**, la barra a **66 %**. **El sistema se queda en la pestaña ② Expediente** — a diferencia
  de cargar la autorización, que siempre salta a ③ (ver B27) — ver la nota al final de este
  bloque.
- **"Cargar" en 2. Comprobante de ingreso**: cuadro de éxito — **"Documento cargado. Faltan:
  Comprobante de domicilio."**, con **"Aceptar"**. El comprobante pasa a **"✓ Cargado
  [fecha]"**, con "Ver" y "Reemplazar"; el contador llega a **"Expediente: 3 de 3 documentos
  obligatorios"**, la barra al **100 %** y cambia de amarillo a **verde**; aparece el recuadro
  **"✓ Expediente completo"**. El botón de abajo sigue diciendo **"Siguiente: ③ Consulta al
  buró →"** — se repite B34, ver Hallazgos.
- **"Cargar" en Comprobante de domicilio (complementario)**, con los 3 obligatorios ya
  cargados: cuadro de éxito distinto a todos los anteriores — **"Expediente completo.
  Siguiente paso: registrar el resultado del buró."**, con **"Aceptar"**. **Es la primera
  carga en la que el cuadro no dice "Faltan: ..."** — confirmado con `SELECT` sobre
  `documentos_expediente`: el comprobante de domicilio quedó cargado como documento `VIGENTE`
  (`COMPROBANTE_DOMICILIO`), y auditado con `CARGAR_DOCUMENTO` como cualquier otro. Al tocar
  **"Aceptar"**, el sistema llevó **automáticamente** a ③ Consulta al buró (`#tabBuro`) —
  **aunque la consulta ya estaba registrada** (850 · A · sin mora, desde antes). Las pestañas
  quedaron todas con ✓: **"① ✓ Datos"**, **"② ✓ Expediente"**, **"③ ✓ Consulta al buró"**,
  **"④ SOL-[año]-[número] (Borrador) →"**; y en ③, el botón grande de abajo ya decía **"④
  SOL-[año]-[número] (Borrador) →"** (antes, con la solicitud sin crear, decía "Siguiente: ④
  Solicitud →") — ver B34 y B35.

**Verificado en el código**:

- **Cómo se calcula la barra, y qué cuenta como obligatorio**: `ControladorClientesServlet.
  mostrarFicha` filtra `itemsExpediente` por `isObligatorio()` (solo cédula, comprobante de
  ingreso y autorización — nunca domicilio) para contar `expedienteCumplidos`/
  `expedienteObligatorios`; `clientes/ficha.jsp` calcula el porcentaje con **división entera**
  (`cumplidos * 100 / total`) — por eso 1 de 3 da exactamente **33 %** (no 33,3) y 2 de 3 da
  **66 %** (no 67), sin redondear.
- **Cuándo la pestaña ④ muestra el número de la solicitud**: en cuanto
  `ServicioSolicitudes.buscarCompromisoAbierto` encuentra una solicitud sin cerrar para ese
  cliente (Borrador, Pendiente de aprobación o Aprobada sin formalizar) — el texto se arma
  literalmente como `solicitud.getCodigoSolicitud() + " (" + estadoSolicitud.getEtiqueta() +
  ")"`. No hace falta que esté enviada ni aprobada: **con solo crearla en Borrador, la pestaña
  ya deja de ofrecer "Nueva solicitud"** y pasa a mostrar la que existe.
- **¿Se valida algo del archivo al cargar la cédula?** Sí, lo mismo que cualquier documento del
  sistema — `ServicioDocumentos.validarArchivo`: extensión declarada (PDF/JPG/PNG), la firma
  real de los primeros bytes del archivo (coherente con la extensión, no solo el nombre), y el
  tamaño máximo (`archivos.tamano_maximo_mb`). No hay ninguna validación específica de "cédula"
  más allá de esa, genérica.
- **¿De dónde sale la vigencia de la cédula?** Del campo **"Vencimiento de la cédula"** de la
  pestaña ① Datos (`persona.fecha_vencimiento_cedula`) — **no** de ninguna fecha del archivo
  cargado (el sistema no lee ni valida esa fecha dentro del PDF/imagen). `ServicioClientes.
  cedulaVencida` compara ese campo contra hoy; si está vencida, la fila de la cédula queda en
  rojo con el motivo aunque haya un documento VIGENTE cargado — mismo criterio ya confirmado
  para el requisito "Cédula de identidad vigente" de la solicitud (ver Paso 1 de
  [02_solicitud.md](02_solicitud.md)).
- **Auditoría de la carga**: `CARGAR_DOCUMENTO` (DOCUMENTO_EXPEDIENTE) — primera carga de ese
  tipo para esta persona; sería `NUEVA_VERSION_DOCUMENTO` si ya hubiera un vigente anterior
  (mismo mecanismo genérico de siempre, sin nada específico para la cédula).
- **Por qué se queda en el Expediente, sin saltar**: mismo mecanismo ya verificado para B27 —
  la carga genérica (`ControladorDocumentosServlet`, rama `/cargar`) solo redirige a `#tabBuro`
  cuando `itemsFaltantesExpediente` queda **vacío** después de esa carga; con comprobante de
  ingreso y domicilio todavía pendientes, se queda en `#tabDocumentos`. La autorización firmada
  (`/cargar-autorizacion-firmada`) es la única que salta **siempre**, sin esa condición — son
  dos rutas con dos criterios distintos para la misma pregunta ("¿adónde vuelvo después de
  cargar?"). Sin resolver cuál de los dos conviene unificar — dato para la fase 2, no un
  hallazgo nuevo (ver B27).
- **Condición exacta de "✓ Expediente completo" y del color de la barra**: la misma variable
  booleana — `pasoExpedienteCompletoFicha` (`expedienteCumplidos == expedienteObligatorios`,
  solo los 3 obligatorios) — controla las **tres** cosas a la vez: la barra pasa de
  `bg-warning` (amarillo) a `bg-success` (verde), aparece el recuadro
  `<div class="alert alert-success">✓ Expediente completo</div>`, y (ver el punto siguiente) la
  pestaña ② se marca con el tilde. Las tres se disparan juntas porque dependen de la misma
  única variable — nunca pueden desincronizarse entre sí.
- **¿La pestaña ② pasa a mostrar "✓" al completarse?** Sí — mismo mecanismo que ya tienen ①,
  ③ y ④: `<% if (pasoExpedienteCompletoFicha) %><span class="text-success">② ✓</span><% } else
  { %>②<% } %> Expediente` — la misma variable de arriba, ninguna lógica nueva.
- **¿Alguna auditoría nueva al completarse?** No — "Expediente completo" es solo un cálculo de
  la pantalla (`ControladorClientesServlet.mostrarFicha`), no escribe nada; la única auditoría
  de este paso fue la del propio `CARGAR_DOCUMENTO` al subir el comprobante de ingreso (ya
  registrada en ese momento, no una segunda vez por "completar" el expediente).
- **¿Qué condición muestra "Expediente completo" en el cuadro, al cargar el complementario?**
  Una tercera variante del mismo cálculo, **más estricta que las otras dos** (la barra/alerta
  de arriba, y el contador "N de 3") — `ControladorDocumentosServlet.doPost` (rama `/cargar`)
  solo arma ese texto cuando `itemsFaltantesExpediente(idPersona)` queda **vacía**, y ese
  método recorre **todos** los `ItemExpediente` sin filtrar por `isObligatorio()` (la misma
  causa raíz de C14) — así que, a diferencia del recuadro "✓ Expediente completo" de la propia
  pestaña (que solo exige los 3 obligatorios), este cuadro de éxito **sí exige también el
  comprobante de domicilio**. Por eso es la primera carga en la que deja de decir "Faltan".
- **¿Por qué el texto no considera que el buró ya está registrado?** Porque no lo mira —
  `ControladorDocumentosServlet.doPost` solo calcula `itemsFaltantesExpediente` (los ítems del
  Expediente); el texto "Siguiente paso: registrar el resultado del buró." está **fijo en el
  código**, sin ninguna consulta al estado real del Buró (`tieneConsultaConEvidenciaRegistrada`
  ni nada parecido). Mismo motivo por el que salta siempre a `#tabBuro`: `volverA =
  "/clientes/" + idPersona + "#tabBuro"` se fija **sin condición**, en el mismo bloque, en
  cuanto `itemsFaltantesExpediente` queda vacía — nunca comprueba si el Buró ya estaba
  completo. Ver B35.
- **Cuándo el botón inferior de ③ cambia a "④ SOL-... (Borrador) →"**: mismo mecanismo ya
  verificado para la pestaña ④ y su nav-pill (`buscarCompromisoAbierto`) — pero en ③ hay,
  además, un **botón grande aparte** (`btn-lg btn-primary`, al pie de la pestaña), que solo se
  dibuja cuando el Paso A (autorización) y el Paso B (buró) están completos
  (`consentimientoVigenteFicha && pasoBuroCompletoFicha`) **y** el usuario puede gestionar
  solicitudes; ese botón sí revisa `textoCompromisoAbiertoFicha`/`enlaceCompromisoAbiertoFicha`
  — con una solicitud abierta, muestra "④ SOL-...(Borrador) →"; sin ninguna, "Siguiente: ④
  Solicitud →". **El botón de ②, en cambio, no tiene ese mismo chequeo** — ver B34.

## Paso 4 — Consulta al buró (RF04)

**Registro de lo observado en uso real (22/10/2026).**

- **Usuario/rol**: Oficial de crédito.
- **Pantalla y ruta**: `/clientes/{id}#tabBuro` (pestaña ③); `POST /clientes/{id}/consulta-buro`.
- **Qué responde el sistema (pantalla completa)**: recuadro **"Autorización firmada por el
  cliente"**, con etiqueta **"Vigente"**: **"✓ Firmada por [cliente] · N.º AUT-… · firmada el
  [fecha] · cargada por [usuario]"**, botón **"Ver documento firmado"**, y **"Vigente hasta
  [fecha] (quedan N días)"** (hoy, cargada el mismo día, quedan los 30 días completos). Debajo,
  **"Paso B · Resultado de la consulta a Informconf"**, con etiqueta **"Pendiente"**: **1.
  Respuesta de Informconf (informe en PDF)** — Falta + "Cargar respuesta"; **2. Puntaje y
  Clasificación** (por defecto "Sin clasificación"); **3. ¿Tiene deudas en mora?** No/Sí; **4.
  Resumen** (opcional); botón **"Registrar consulta"**, deshabilitado hasta completar. Al pie,
  **"Historial de consultas (N)"**.
- **"Cargar respuesta" (ítem 1 del Paso B)**: el oficial toca el botón, sube el informe de
  Informconf en PDF. Cuadro de éxito: **"Documento cargado. Complete el puntaje, la
  clasificación y si tiene mora, y toque Registrar consulta."**, con **"Aceptar"**. El PDF
  **se guarda al cargarlo, antes y por separado de "Registrar consulta"** — el ítem 1 pasa a
  mostrar el nombre del archivo, un botón **"Ver"** y, si hace falta reemplazarlo, **"Reemplazar"**
  (con confirmación, porque ya hay uno vigente).
- **"Registrar consulta"**: con puntaje (850), clasificación (A), ¿tiene deudas en mora? (No) y
  resumen completos, el botón se habilita. Al tocarlo: cuadro de éxito — **"Consulta registrada.
  850 · A · sin mora."**, con **"Aceptar"**. La pestaña pasa a **"③ ✓ Consulta al buró"**; la
  etiqueta del Paso B pasa de "🟠 Pendiente" a **"✅ Registrada"**; recuadro **"Consulta
  registrada: 850 · A · sin mora"**; botones **"Registrar una nueva consulta"** y **"Siguiente:
  ④ Solicitud →"**; **"Historial de consultas (1)"**. La pestaña ② Expediente sigue **sin
  tilde** (faltan cédula y comprobante de ingreso) — pero el sistema igual ofrece "Siguiente: ④
  Solicitud →" — ver B28 en Hallazgos.
- **Regla de negocio**: ver [RF04_buro.md](../funcionamiento/RF04_buro.md) §1-§2.
- **Qué cambia en la base**: **al cargar el informe** (ya, con solo ese paso) —
  `archivos_binarios` + `documentos_expediente` (tipo `RESULTADO_CONSULTA_BURO`, contexto
  `PERSONA`, `estado_documento = VIGENTE`). **Al "Registrar consulta"**: `INSERT` en
  `consultas_crediticias` — `id_persona`, `id_consentimiento` (el de la autorización vigente),
  `proveedor_buro` ("Informconf"), `puntaje_obtenido`, `clasificacion_obtenida`,
  `indicador_mora`, `resumen_resultado`, `id_documento_resultado`, `consultado_por`,
  `fecha_hora_consulta` — confirmado con `SELECT` sobre el segundo cliente de práctica: fila
  real con 850,00 · A · `indicador_mora = f` · `id_documento_resultado = 12` (el mismo informe
  cargado en el paso anterior).
- **Cómo se vincula con el informe ya cargado (cierra C15)**: por la columna
  `consultas_crediticias.id_documento_resultado`, no por nombre de archivo ni por fecha — la
  ficha precarga esa columna en un campo oculto del formulario con el ID del documento
  `RESULTADO_CONSULTA_BURO` **vigente** de la persona (`ServicioClientes.
  construirItemInformeBuro`), nunca lo escribe a mano el oficial;
  `ServicioConsultaCrediticia.registrarConsulta` exige ese ID
  (`validarDocumentoResultado`, rechaza si viene nulo). **Sí queda vinculado** — confirmado con
  el `SELECT` de arriba: la fila de `consultas_crediticias` apunta exactamente al documento que
  estaba "huérfano" en el paso anterior.
- **¿Qué pasa con el PDF si se registra una nueva consulta?** El documento en sí **no se toca**
  al registrar — solo cambia si el oficial sube un PDF nuevo aparte ("Reemplazar"), que marca el
  anterior `REEMPLAZADO` (mismo versionado de siempre). **"Registrar una nueva consulta"** es
  puramente un botón de JavaScript que vuelve a mostrar el mismo formulario (ya estaba oculto
  por `pasoBuroCompletoFicha`) — no toca ni cierra la fila anterior de `consultas_crediticias`
  en la base: ambas quedan, cada una en el historial, con su propio `id_documento_resultado` (el
  mismo documento si no se reemplazó nada, o el nuevo si sí).
- **Qué registra la auditoría**: `CARGAR_DOCUMENTO` (DOCUMENTO_EXPEDIENTE) al cargar el informe
  — mismo evento genérico que cualquier documento del expediente, sin uno propio para "informe
  de buró"; `REGISTRAR_CONSULTA_BURO` (CONSULTA_CREDITICIA, entidad = la consulta, no la
  persona) al registrar la consulta, aparte — confirmado con `SELECT` sobre
  `auditoria_operaciones`: una sola fila nueva, justo con ese código.
- **¿Alguna regla usa el puntaje, la clasificación o la mora?** Verificado en
  `ServicioAprobacionCredito`/`ServicioSolicitudes` — **solo `indicador_mora = true`
  bloquea de verdad** (RN-18), en dos momentos: `enviarAAprobacion` (rechaza con "El cliente
  figura con deudas en Informconf. No se puede otorgar el crédito.") y
  `aprobar` (`tieneMoraVinculada`, rechaza con "...rechace la solicitud con el motivo
  'Informconf: figura con deudas'."). La **clasificación D o E (sin mora)** no bloquea nada —
  solo genera un aviso visual para el gerente (`construirAvisoBuro`, franja naranja "Riesgo
  alto según Informconf: clasificación [D/E]"), que queda a su criterio. El **puntaje** no se
  usa en ninguna regla ni aviso — es puramente informativo, solo se muestra en pantalla,
  informes y el detalle de auditoría.
- **Qué sigue**: "④ Solicitud" — ver [02_solicitud.md](02_solicitud.md).

## C15 — Informe de Informconf cargado, consulta nunca registrada: ¿queda huérfano?

**Verificado en código y con `SELECT` en `sgcpc_db_pruebas` (estado real del segundo cliente de
práctica en este mismo momento: informe cargado, consulta sin registrar todavía).**

- **Qué queda en la base**: una fila VIGENTE en `documentos_expediente` (tipo
  `RESULTADO_CONSULTA_BURO`) con su `archivos_binarios`, y **ninguna fila en
  `consultas_crediticias`** — confirmado con `SELECT` (0 filas para esta persona). No hay
  ninguna restricción de base que obligue a que un documento de este tipo termine vinculado a
  una consulta; el vínculo es opcional y se crea recién al "Registrar consulta".
- **Qué muestra la pantalla al volver**: el ítem 1 del Paso B ya no dice "Falta" — muestra el
  nombre del archivo cargado, "Ver" y "Reemplazar" (`ItemExpediente.isCumplido()` da `true` con
  solo el documento vigente, sin mirar si hay consulta). Pero la tarjeta completa del Paso B
  sigue con la etiqueta **"🟠 Pendiente"** (no "✅ Registrada"), y el botón "Registrar consulta"
  sigue deshabilitado hasta completar puntaje/clasificación/mora — el ID del documento ya
  cargado queda precargado en el campo oculto, listo para cuando se complete el resto.
- **¿El informe queda huérfano?** En la base, sí, en el sentido de que nada lo referencia desde
  `consultas_crediticias` — pero no es un error ni un dato corrupto: sigue siendo un documento
  VIGENTE y utilizable, visible y reemplazable desde la pantalla, exactamente como cualquier
  documento del expediente cargado de antemano.
- **¿Hay algún aviso de "consulta a medias"?** No uno dedicado — el único indicio es la
  etiqueta "🟠 Pendiente" de la tarjeta completa del Paso B (la misma que ya estaba antes de
  cargar nada) y que el ítem 1 ya no dice "Falta". No hay ningún mensaje que diga, por ejemplo,
  "ya cargó el informe, complete el resto para registrar la consulta".
- **¿Afecta algo del circuito mientras no se registre?** No — `ServicioSolicitudes.
  agregarFaltantesBuro`/`tieneConsultaConEvidenciaRegistrada` exige una fila real de
  `consultas_crediticias` con evidencia posterior a la autorización vigente; un documento
  cargado sin consulta no cuenta como "consulta registrada" para ningún gate (ni el checklist
  previo al envío, ni "Enviar a aprobación"). Mientras tanto, el oficial puede cargar otro
  informe encima ("Reemplazar") sin ninguna restricción adicional por tener uno sin usar.
- **Clasificación: pendiente** — no decido acá si esto es A, B o C; queda para que Joel o el
  dueño de Capital Crédito lo evalúe (el anteproyecto no dice nada sobre qué hacer con un
  informe cargado y nunca usado).

**Cierre de C15 (22/10/2026): Cubierto.** Se completó el ciclo con el mismo cliente de
práctica — al tocar "Registrar consulta", la fila nueva de `consultas_crediticias` quedó
vinculada exactamente al documento que estaba sin usar (`id_documento_resultado = 12`,
confirmado con `SELECT`), sin perder nada ni duplicar el informe. El estado "huérfano"
observado antes era solo transitorio, mientras el oficial todavía no completaba el formulario
— no es un hueco de datos ni deja un registro inconsistente: el documento esperó, sin afectar
ningún gate del circuito, hasta que se usó. No hay ninguna corrección pendiente — la única
mejora de uso identificada (que no hay un aviso de "consulta a medias" mientras se espera) ya
quedó anotada arriba como mejora de uso, no como riesgo.

## Paso 5 — Baja y reactivación de clientes (gerente) — verificado el 16/10/2026, pendiente de recorrer con este rol

Lista de clientes → **Dar de baja** (motivo y detalle) → confirmación. Rechaza si el cliente
tiene préstamo activo o en mora, o una solicitud abierta. **Reactivar** conserva la última baja
como historial. No forma parte de la entrevista de hoy (es una acción del gerente, no del
oficial) — queda a reconfirmar cuando se recorra con ese rol.

Ver el detalle completo y las reglas de negocio en
[RF02_clientes.md](../funcionamiento/RF02_clientes.md).

## Hallazgos de esta escena

Separados de "Cómo funciona hoy" (arriba, ficha por ficha). Clasificación **A** = no cumple el
anteproyecto; **B** = mejora de uso, no cambia el RF; **C** = a confirmar leyendo el RF (se
resuelve más abajo, sin corregir nada).

### Cotizador (RF01)

- **B** — Sin accesos directos desde el Panel ("Cotizar", "+ Nuevo cliente"); ver la nota de
  contexto arriba (Escena 00).
- **B** — El resultado se lee debajo del formulario, no al costado.
- **B** — Falta un botón "Siguiente: verificar si califica →" que lleve monto/plazo/nombre/C.I.
  hacia Precalificación → Alta → Solicitud (hoy cada pantalla se llena de nuevo, sin ningún
  dato que viaje de una a la otra — ver también el hallazgo C de la fecha, más abajo).
- **B** — El cotizador no muestra los requisitos documentales, los próximos pasos ("si trae
  todo, sale en el día"), "sin comisiones ni gastos" ni "puede pagar antes de lo pactado".

### Precalificación / papel de requisitos (RF02)

- **Decisión tomada: obligatorio — implementación pendiente (fase 2)** (ver el detalle en
  "Hallazgos tipo C, clasificados" más abajo): el papel de requisitos impreso ya lista el
  comprobante de domicilio como obligatorio; Joel decidió el 21/10/2026, durante este
  recorrido, que el sistema pase a tratarlo igual (reemplaza la "opción b" del 19/10/2026, que
  lo dejaba complementario). Sin implementar todavía — ver `AVANCE.md` y `RF03_expediente.md`
  §7.
- **B** — El papel de requisitos no menciona los datos del empleador, las referencias, el
  cónyuge (si aplica), el aviso de que se consulta el buró, el límite del 40 % de afectación, ni
  "cédula vigente".
- **B25 (baja prioridad)** — "Imprimir requisitos" no deja auditoría — verificado de punta a
  punta en `ServicioClientes.generarInformeRequisitosPdf`: ningún `servicioAuditoria.registrar`.
  A diferencia del PDF del cotizador, que sí audita `EMITIR_COTIZACION_PDF` (ver Paso 1), acá no
  queda ningún rastro de que se emitió el papel. Sugerir auditar también la emisión de
  requisitos, por coherencia con el resto de los imprimibles del sistema.

### Datos personales (RF02)

- **B** — La cédula duplicada se detecta al tocar "Registrar cliente", no apenas se escribe el
  número (podría avisarse antes, al salir del campo).
- **C13 — Cubierto (aclarado 22/10/2026, cruzando dos verificaciones ya hechas)**: editar los
  datos del cliente después del alta solo **avisa** si deja de calificar y permite guardar
  igual — `ServicioClientes.actualizar` nunca bloquea por esto, salvo que
  (`bloquearSiCambianDatosDeEvaluacion`/`cambiaronDatosDeEvaluacion`) haya una evaluación en
  curso (solicitud `PENDIENTE_APROBACION`/`APROBADA` sin formalizar, o préstamo
  `PENDIENTE_DESEMBOLSO`) y se toquen los campos de evaluación (fecha de nacimiento, ciudad/
  zona, situación laboral, presenta-comprobante, ingreso neto, cuota de otras deudas, datos del
  empleador) — ahí sí bloquea, antes de escribir nada. **Lo que sí se puede editar en ese
  estado**: nombre, teléfono, correo, RUC, domicilio (calle/barrio/referencia, no la ciudad),
  cónyuge, referencias, y la **fecha de vencimiento de la cédula** (no está en la lista
  bloqueada — cambiarla solo marca el escaneo vigente como `REEMPLAZADO`, sin bloquear nada).
  - **Cruce con el checklist de 8 requisitos**: `ServicioSolicitudes.
    construirChecklistPrevioEnvio` es el MISMO método que usa `ControladorAprobacionesServlet`
    para la pantalla de revisión del gerente — se recalcula contra el estado actual en cada
    carga de esa página, nunca contra una foto vieja. Sus 8 ítems son exactamente: edad, zona,
    situación laboral y comprobante (los 4 campos que `cambiaronDatosDeEvaluacion` ya bloquea
    mientras hay evaluación en curso) más cédula vigente, autorización de buró vigente,
    consulta con evidencia y sin mora (estos 4 dependen de documentos/consultas, nunca
    bloqueados por la edición de `Persona` — pero tampoco necesitan estarlo, porque esta
    pantalla los recalcula en vivo).
  - **Conclusión**: los únicos campos capaces de hacer que el cliente deje de cumplir la
    política (edad/zona/situación laboral/comprobante) están bloqueados mientras hay una
    evaluación en curso; los que sí pueden cambiar sin bloqueo (cédula, documentos, buró) son
    justamente los que la pantalla del gerente vuelve a calcular en vivo antes de decidir. **No
    hay un hueco real.** Queda un residual teórico, no un riesgo práctico: `aprobar()`
    (`ServicioAprobacionCredito`) no repite este checklist de 8 en el instante mismo de
    aprobar — solo revalida tope de tasa, afectación congelada y mora (RN-18) — así que si algo
    de buró/cédula cambiara en la angosta ventana entre que el gerente abre la revisión y hace
    clic en "Aprobar", no habría un último chequeo server-side de esos 4 ítems puntuales. Como
    los campos que de verdad importan para la política ya están bloqueados y la autorización de
    buró tiene una ventana de 30 días (no una carrera de segundos), este residual no amerita
    clasificación A/B — queda anotado como residual teórico, no como hallazgo nuevo.

### Expediente (RF03 / RF04)

- **Decisión tomada: obligatorio — implementación pendiente (fase 2)** (ver más abajo): mismo
  hallazgo que en Precalificación, aplicado acá al contador "Faltan" (hoy "3 de 3", pasará a
  "4 de 4" cuando se implemente).
- **C14 — CERRADO el 28/10/2026** (antes "Resuelto por decisión", 22/10/2026): se implementó la
  decisión del 21/10/2026 y el comprobante de domicilio pasó a **obligatorio**, con lo que los dos
  criterios coinciden — el contador ahora cuenta "N de **4**" y el mensaje "Faltan: …" sigue
  listando el domicilio, que es lo correcto. **No hizo falta tocar `itemsFaltantesExpediente`**:
  se cumplió la primera de las dos salidas que el propio hallazgo anticipaba. Texto original del
  hallazgo: El cuadro posterior a cargar la autorización
  lista **"Comprobante de domicilio"** entre los faltantes, aunque la ficha lo trate como
  complementario en todos los demás lugares (contador "N de 3", barra de avance). **Causa
  raíz**: dos métodos usan dos criterios distintos sobre la misma lista de `ItemExpediente` —
  el contador (`ControladorClientesServlet.mostrarFicha`) filtra por `isObligatorio()`; el
  mensaje "Faltan: ..." (`ControladorDocumentosServlet.itemsFaltantesExpediente`, usado tanto
  por la carga genérica como por "Cargar autorización firmada") agrega **todos** los
  `ItemExpediente` no cumplidos, sin mirar `isObligatorio()`. No es un error de datos, es una
  inconsistencia entre dos lugares del código que debieran usar el mismo filtro. Revisar junto
  con la decisión del 21/10/2026 (domicilio obligatorio, fase 2) — si se implementa esa
  decisión, el mensaje "Faltan" queda correcto para los dos casos sin tocar nada más; si no se
  implementa, igual conviene que `itemsFaltantesExpediente` filtre por `isObligatorio()` para
  que los dos mensajes coincidan. **Se repite en cada carga (confirmado 22/10/2026)**: al
  cargar la cédula, el mismo cuadro volvió a listar "Comprobante de domicilio" entre los
  faltantes — no es un caso aislado de la autorización, es el comportamiento de
  `itemsFaltantesExpediente` en **cualquier** carga genérica (misma causa raíz, mismo método).
  **Ejemplo más claro de la contradicción (22/10/2026, al completar el expediente)**: al cargar
  el comprobante de ingreso (el último de los 3 obligatorios), **en la misma pantalla
  conviven** el recuadro verde **"✓ Expediente completo"** y el cuadro de éxito **"Documento
  cargado. Faltan: Comprobante de domicilio."** — dos mensajes sobre el mismo expediente, uno
  dice "completo" y el otro dice "falta algo", y los dos están técnicamente en lo correcto
  según su propio criterio (uno mira solo obligatorios, el otro mira todo). La decisión del
  21/10/2026 (domicilio obligatorio, fase 2) resuelve la contradicción **si se aplica en los
  dos lugares** (el contador/barra de `ControladorClientesServlet.mostrarFicha` Y la lista de
  `ControladorDocumentosServlet.itemsFaltantesExpediente`) — aplicarla en uno solo de los dos
  dejaría la misma inconsistencia, solo que al revés. **Dato adicional, confirmado al cargar
  el complementario (22/10/2026)**: el cuadro de éxito de la carga **solo deja de decir
  "Faltan: ..." cuando también se carga el comprobante de domicilio** — es decir, el criterio
  de `itemsFaltantesExpediente` (sin filtrar por `isObligatorio()`) exige, en los hechos, los
  **4** documentos (los 3 obligatorios y el complementario), aunque el resto del sistema (el
  contador "N de 3", la barra, el recuadro de la propia pestaña) siga tratando al domicilio
  como no obligatorio. Es la pieza que faltaba para ver completa la inconsistencia: no son
  solo dos criterios distintos, es que uno de ellos (el de este cuadro) termina siendo, en la
  práctica, más exigente que lo que el propio sistema dice que es obligatorio.
  **Cierre (22/10/2026)**: la decisión del 21/10/2026 (comprobante de domicilio obligatorio,
  fase 2, todavía sin implementar — ver `AVANCE.md` y `RF03_expediente.md` §7) **unifica los
  dos criterios de raíz, sin necesitar ninguna decisión nueva**: una vez implementada, el
  domicilio pasa a ser obligatorio en TODO el sistema, así que el contador/barra de
  `ControladorClientesServlet.mostrarFicha` (que filtra por `isObligatorio()`) y la lista de
  `ControladorDocumentosServlet.itemsFaltantesExpediente` (que nunca filtró) quedan diciendo
  exactamente lo mismo, sin tocar ninguno de los dos métodos — la inconsistencia era un síntoma
  de que `itemsFaltantesExpediente` ya trataba al domicilio como obligatorio, mientras el resto
  del sistema todavía no. **Queda como hallazgo B para la fase 2**: cuando se implemente esa
  decisión, conviene verificar en código que los dos lugares coincidan de verdad antes de cerrar
  el cambio (no asumir que unificar un filtro basta sin probarlo), y queda la mejora ya anotada
  en B26 de ubicar la autorización primera en la lista.
- **B26** — La autorización de consulta al buró aparece **última** en la lista del Expediente
  (fila 3 de 3), pero en la práctica es lo **primero** que hay que resolver: sin ella firmada no
  se puede consultar Informconf, y toda la secuencia real observada hoy (generar → firmar →
  cargar) ocurre antes de cargar cédula o comprobante de ingreso. Sugerir ubicarla primero en la
  lista, o marcarla con un indicador "Empiece por aquí".
- **B27 (verificado, mecanismo confirmado — ampliado 22/10/2026)** — Después de cargar la
  autorización firmada, el sistema salta **siempre** a la pestaña ③ Consulta al buró
  (`ControladorDocumentosServlet.procesarCargarAutorizacionFirmada`, redirect fijo a
  `#tabBuro`, sin mirar si el expediente quedó completo), sin ningún aviso de que el expediente
  sigue incompleto. **El mismo salto automático ocurre también al completarse el expediente
  entero** (no solo al cargar la autorización) — confirmado hoy al cargar el último documento
  (el comprobante de domicilio, complementario): el mismo bloque de
  `ControladorDocumentosServlet.doPost` fija `volverA = ".../#tabBuro"` sin condición, en
  cuanto `itemsFaltantesExpediente` queda vacía, sea cual sea el documento que la completó.
  Sugerir un mensaje del tipo **"Siguiente paso: consulta al buró. Pendientes en el
  expediente: [lista]."** en vez del "Faltan: ..." genérico actual — y, cuando el salto
  ocurra con el buró ya hecho, que el mensaje diga qué sigue de verdad (ver B35, que separa
  ese caso puntual).
- **B34 (actualizado 22/10/2026, con la causa exacta)** — Con el buró ya registrado y una
  solicitud en Borrador para el cliente, el botón de abajo del Expediente sigue ofreciendo
  **"Siguiente: ③ Consulta al buró →"** — ya no tiene sentido, el buró está hecho y lo que
  sigue de verdad es volver a la solicitud. **Se repite, incluso con el Expediente ya
  completo** (3 de 3) — confirmado que no depende de si el Expediente está completo, solo del
  Buró. **La inconsistencia exacta, verificada hoy**: en ③ Consulta al buró **ya existe** este
  mismo chequeo — el botón grande del pie de esa pestaña sí revisa
  `textoCompromisoAbiertoFicha`/`enlaceCompromisoAbiertoFicha` y cambia a "④ SOL-...(Borrador)
  →" cuando corresponde (ver "Verificado en el código" arriba) — **pero el botón equivalente
  de ② Expediente nunca se tocó con ese mismo criterio**, sigue siendo un botón fijo
  (`botonSiguienteDesdeExpediente`) que solo dispara un clic sobre la pestaña ③, sin mirar
  `textoCompromisoAbiertoFicha` en absoluto. Sugerir unificar: que el botón de ② use el mismo
  chequeo que ya tiene el de ③, y ofrezca **"Volver a la solicitud [código]"** cuando
  corresponda.
- **B35** — El cuadro "Expediente completo. Siguiente paso: registrar el resultado del buró."
  (al cargar el comprobante de domicilio, el último pendiente) dice ese texto y salta al buró
  **aunque la consulta ya esté registrada** — verificado que ni el texto ni el salto consultan
  el estado real del Buró, están fijos en el código
  (`ControladorDocumentosServlet.doPost`, ver "Verificado en el código" arriba). Sugerir que,
  antes de armar ese mensaje, el controlador consulte si el Buró ya está completo
  (`tieneConsultaConEvidenciaRegistrada`, el mismo método que ya usa el checklist de la
  solicitud) y, si lo está, indique el paso realmente pendiente — por ejemplo, **"Expediente
  completo. Siguiente paso: enviar la solicitud SOL-[número] a aprobación."**, con el enlace
  correspondiente, en vez de mandar siempre a `#tabBuro`.
- **Dato para unificar en fase 2 (sin ser, en sí, un hallazgo nuevo)**: confirmado que el
  comportamiento después de cargar un documento **no es el mismo para todos** — la autorización
  salta siempre a `#tabBuro` (B27); la cédula (y, por el mismo mecanismo genérico, cualquier
  otro documento del Expediente) se queda en `#tabDocumentos` salvo que esa carga complete los
  3 obligatorios. Dos rutas (`/cargar` y `/cargar-autorizacion-firmada`) con dos criterios
  distintos para la misma pregunta — no se propone acá cuál de los dos debería ganar, solo se
  deja registrada la diferencia para que se decida en la fase 2.
- **B** — Sin indicador "Cargando…" ni el botón desactivado mientras se sube un archivo.
- **Confirmación de B13, con un ejemplo real (22/10/2026)** — un barrio cargado con una coma al
  final (ej. "Barcequillo,") se imprime en la autorización como **"Barcequillo,,"** (coma
  duplicada) — un documento **legal, firmado por el cliente**. Verificado que el PDF de la
  autorización sí usa el mismo texto de domicilio armado por JOIN que el resto de los
  documentos (`ServicioClientes.obtenerTextoDomicilio`, llamado desde
  `construirPdfAutorizacionBuro`) — la causa no es una fuente de texto distinta, sino que el
  armador real, `ServicioClientes.construirTextoDomicilio`, concatena las partes
  (dirección, barrio, ciudad, departamento) con `String.join(", ", partes)` sin quitar primero
  la puntuación sobrante de la parte anterior (coherente con lo ya confirmado: `normalizarTextoLibre` nunca recorta puntuación, ver
  el registro del cliente, Paso 2).

### Buró (RF04)

- **B** — El puntaje (850) se muestra sin indicar el rango de la escala.
- **Descartado por Joel** — comparar la cuota declarada por el cliente contra lo que figura en
  Informconf: no se implementa; la oficial lo anota en el resumen y el gerente lo evalúa al
  decidir.
- **B28 (familia de B27)** — Al registrar la consulta, el cuadro de éxito ("Consulta
  registrada. 850 · A · sin mora.") no indica qué sigue, y el botón **"Siguiente: ④ Solicitud
  →"** aparece habilitado aunque la pestaña ② Expediente siga incompleta (hoy, sin cédula ni
  comprobante de ingreso cargados) — verificado que esto no bloquea nada en el momento (el
  expediente solo se exige al "Enviar a aprobación", ver C13/C14), pero la pantalla no lo avisa
  acá, donde el oficial podría pensar que ya puede avanzar sin más vueltas. Sugerir mostrar
  **"Pendientes en el expediente: [lista]"** antes de ofrecer el botón hacia la Solicitud,
  mismo criterio que B27 propone para el salto automático después de cargar la autorización.

## Hallazgos tipo C, clasificados (lectura de los RF, sin modificarlos ni corregir nada)

**Comprobante de domicilio obligatorio en el papel vs. complementario en el sistema — resuelto
con una decisión nueva (21/10/2026).** Lectura hecha durante el recorrido: desde el 19/10/2026
(tanda de usabilidad), el sistema trataba como obligatorios solo cédula, comprobante de ingreso
y autorización de buró, dejando el comprobante de domicilio "(complementario)" — el
anteproyecto (RF03: "indicar qué documentos faltan") no fija esa lista, es una decisión de
diseño. **Joel decidió el 21/10/2026, durante este recorrido, que el comprobante de domicilio
pase a ser obligatorio** (reemplaza la "opción b" del 19/10/2026) — como el anteproyecto no fija
la lista de documentos, esta decisión no toca `docs/RF_ANTEPROYECTO.md`, solo el registro interno
de decisiones (`AVANCE.md`, `RF03_expediente.md` §7).
**Estado: IMPLEMENTADA Y PROBADA EN PANTALLA el 28/10/2026.** El expediente pasó a **"N de 4"**,
el comprobante de domicilio se numera como **3.** con "(obligatorio)" y badge "Falta", y
**bloquea "Enviar a aprobación"** con *"Falta cargar el comprobante de domicilio del cliente en
su expediente."* Probado por Joel el mismo día: la ficha de un cliente ya cargado pasó a "3 de 4"
(prueba A) y un cliente nuevo fue bloqueado y después desbloqueado al cargar el documento
(prueba B, `SOL-2026-0005` enviada). Cierra **C14**. Detalle en `RF03_expediente.md` §7 y §9, y
en la entrada del 28/10/2026 de `AVANCE.md`.

De esa prueba salieron tres hallazgos nuevos, los tres en `FASE2_PLAN.md`: **B88** (flujo guiado
— a dónde lleva el sistema después de cada carga y qué dice; pedido explícito de Joel, prioridad
alta), **B89** ("No cumple validaciones preliminares" no dice cuál falta) y **B90** (orden de la
lista del expediente, pendiente de la decisión de Joel).

**29/10/2026 — lo que vi en pantalla con el flujo guiado y el orden nuevo del expediente**
(oficial Lorenzo Ramírez, cliente nuevo *Prueba de Flujo Jueves 29*, C.I. 1.222.333, persona 6;
estado de los ítems en [FASE2_PLAN.md](FASE2_PLAN.md) — B88, B89, B90, B27, B28, B34, B35):

- La pestaña ② ahora lista **autorización 1, cédula 2, ingreso 3, domicilio 4**, con "0 de 4".
- El alta avisó *"Siguiente paso: generar la autorización, hacerla firmar y cargarla."*
- `AUT-2026-00005` cargada → el sistema me llevó a **③** con *"Siguiente paso: registrar el
  resultado del buró."*
- Cargar la cédula y el comprobante de ingreso me **dejó en ②**, con *"Faltan: …"* y
  *"Pendiente también: registrar el resultado del buró."*
- Registrar el buró (850 · A · sin mora) me llevó a **②**: *"Consulta registrada. 850 · A · sin
  mora — Falta: Comprobante de domicilio."*
- Con `SOL-2026-0006` ya creada, cargar el domicilio me llevó **a la ficha de la solicitud**:
  *"Siguiente paso: enviar SOL-2026-0006 a aprobación."* Enviada a las 11:43.
- En la ficha de la solicitud, el cartel amarillo decía *"No cumple validaciones preliminares.
  Falta: Comprobante de domicilio cargado."*; con todo cargado pasó a verde, 9/9 ✓.
- En `Ana María Prueba` (persona 4, sin autorización): el botón *"Siguiente: ② Expediente →"*
  aparece en **① Datos** y **no** en ②, y el enlace *"Ver ficha completa / documentos"* de
  `SOL-2026-0007` abrió `/clientes/4#tabDocumentos`, directo en ②.

Datos que quedaron de esta prueba (se van con el vaciado previo a la entrega): persona 6 con
`SOL-2026-0006` (PENDIENTE_APROBACION) y `SOL-2026-0007` de Ana (BORRADOR).

**29/10/2026, 20:23-20:32 — el aviso de los datos congelados (B91).** Con `SOL-2026-0007` ya
enviada a aprobación, abrí la ficha de Ana en ① Datos: los dos banners amarillos dicen *"**Los
datos en gris** no se pueden modificar porque la solicitud SOL-2026-0007 está pendiente de
aprobación…"*, y los campos congelados se ven efectivamente en gris mientras el resto (nombres,
cédula, contacto, referencias, otros ingresos y gastos) se sigue editando. El modal del servidor
—el otro texto, *"Los datos de la evaluación…"*— **no se pudo ver**: por pantalla no hay forma de
dispararlo, justo porque los campos grises no se editan. Estado del ítem en
[FASE2_PLAN.md](FASE2_PLAN.md).

## Hallazgo compartido con la Escena 02 (fecha del primer vencimiento)

**Fecha propuesta en el cotizador (10/11) distinta de la fecha de la solicitud (21/11)** — ver
la clasificación completa en [02_solicitud.md](02_solicitud.md) § Hallazgos tipo C (involucra
tanto a RF01 como a RF05, se resuelve una sola vez ahí para no duplicar el análisis).
