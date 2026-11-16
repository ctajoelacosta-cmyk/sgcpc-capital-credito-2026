# Anexo — Qué pasa por detrás (Escenas 01 y 02, rol oficial)

Complementa [01_llega_el_cliente.md](01_llega_el_cliente.md) y
[02_solicitud.md](02_solicitud.md): mismo tramo (login hasta "Enviar a aprobación", rol
oficial), mirando lo que pasa en el servidor y en la base en cada paso — tablas leídas/escritas,
parámetros y clases que validan cada regla, qué queda en la auditoría, dónde se guardan los
archivos y de dónde salen los números. **Verificado leyendo el código real** (solo lectura, sin
ejecutar nada ni cambiar nada); donde no pude confirmarlo con una lectura directa, queda
marcado **"a confirmar"**.

## 0. Login (RF36 — Escena 00, todavía sin recorrer formalmente)

- **Lee**: `usuarios` (`UsuarioDAO.buscarPorNombreUsuario`).
- **Escribe**: `usuarios` — si el acceso es correcto, `intentos_fallidos = 0`,
  `fecha_ultimo_acceso = now()`; si falla, `intentos_fallidos` suma 1 y, al llegar al límite,
  `estado_cuenta` pasa a `BLOQUEADA`.
- **Parámetros / clase y método**: `seguridad.intentos_login` (máximo de intentos),
  `seguridad.minutos_bloqueo` (duración del bloqueo) — `ServicioAutenticacion.intentarAcceso`.
- **Auditoría**: `ACCESO_CONCEDIDO` ("Inicio de sesión") si entra bien; según el motivo del
  rechazo, `ACCESO_FALLIDO` ("Inicio de sesión fallido: contraseña incorrecta"),
  `ACCESO_CUENTA_BLOQUEADA`, `ACCESO_CUENTA_INACTIVA` o `CUENTA_BLOQUEADA` ("Bloqueo automático
  de cuenta", cuando este intento es el que la bloquea) — entidad USUARIO.
- **Archivos**: ninguno.
- **Numeración**: ninguna.

## 1. Cotizador (RF01)

- **Lee**: `parametros_sistema` (`credito.monto_minimo`/`maximo`, `credito.plazo_minimo`/
  `maximo`, `credito.tea_empresa`, `formalizacion.dias_min_primer_vencimiento`/
  `dias_max_primer_vencimiento`), `limites_tasa_usura` (tope legal vigente), `feriados`
  (fechas activas, para que el plan de cuotas no caiga en feriado).
- **Escribe**: nada — `ServicioCotizacion.cotizar` no hace ningún `INSERT`/`UPDATE`, cumple
  literalmente "sin registrar ningún dato" del anteproyecto.
- **Reglas de negocio**: rango de monto/plazo (`ServicioCotizacion.validarMontoDentroDeLimites`/
  `validarPlazoDentroDeLimites`); rango de fecha del primer vencimiento, 20-45 días
  (`ServicioCotizacion.validarRangoFechaPrimerVencimiento`, delega en
  `ServicioFormalizacion.validarPrimerVencimientoFormalizacion` — **el mismo método que usa
  Formalización, RF11**); tope legal de tasa (`ServicioMotorFinanciero.validarLimiteTasa`
  contra `limites_tasa_usura`).
- **Auditoría**: ninguna — única pantalla operativa sin evento propio.
- **Archivos**: el PDF ("Imprimir") se genera al vuelo en cada clic, nunca se guarda — ni en
  disco ni en la base.
- **Numeración**: ninguna.

## 2. Imprimir requisitos (RF02, botón de Precalificación)

- **Lee**: `parametros_sistema` (`credito.edad_minima`/`maxima`,
  `credito.departamentos_habilitados`, `empresa.telefono`, `empresa.whatsapp`, datos de la
  empresa para el encabezado del PDF).
- **Escribe**: nada.
- **Reglas de negocio**: la lista de documentos sugeridos (cédula, comprobante de domicilio, y
  el comprobante de ingreso según situación laboral) **no viene de una tabla** —
  `ServicioClientes.documentosSugeridosPorSituacionLaboral` la arma con texto fijo en el código,
  no lee `tipos_documento` ni ninguna otra tabla. Esto explica por qué este papel puede
  desalinearse de la lista real de obligatorios (`CODIGOS_EXPEDIENTE_OBLIGATORIOS`, ver el paso
  7 más abajo) — son dos fuentes de texto completamente separadas, sin ningún vínculo en el
  código entre ellas.
- **Auditoría**: ninguna.
- **Archivos**: el PDF se genera al vuelo, no se guarda.
- **Numeración**: ninguna.

## 3. Precalificación (dentro de "+ Nuevo cliente", RF02)

- **Lee**: `parametros_sistema` (`credito.edad_minima`/`maxima`,
  `credito.departamentos_habilitados`, `credito.situaciones_laborales_admitidas`),
  `departamentos` (activos, para la lista desplegable).
- **Escribe**: nada — es una evaluación en vivo, antes de guardar cualquier dato del cliente.
- **Reglas de negocio**: `ServicioClientes.evaluarPrecalificacion`/`listarMotivosNoCalifica`
  (edad, departamento, situación laboral, ¿presenta comprobante?), con los mismos 3 parámetros
  de arriba.
- **Auditoría**: ninguna.
- **Archivos**: ninguno.
- **Numeración**: ninguna.

## 4. Registrar cliente (RF02)

- **Lee**: `personas` (verificar cédula no repetida), `parametros_sistema` (los mismos de
  Precalificación), `ciudades`/`departamentos` (para el domicilio).
- **Escribe** (una sola transacción, `ServicioClientes.alta`): `INSERT` en `personas` (nueva
  fila, estado `ACTIVA`), `personas_domicilio`, `personas_laborales` (si aplica, con empleador/
  ingreso/deudas), `personas_referencias` (2 filas). `personas_conyuge` no se escribe en este
  caso (cliente soltera).
- **Reglas de negocio**: unicidad de cédula (`PersonaDAO.buscarPorCedula`); el resto de las
  validaciones ya se hicieron en Precalificación, no se repiten acá salvo la cédula.
- **Auditoría**: `ALTA_CLIENTE` ("Alta de cliente"), entidad PERSONA.
- **Archivos**: ninguno todavía (los documentos se cargan en el paso siguiente).
- **Numeración**: ninguna — el identificador del cliente es el `id_persona` autonumérico de la
  base, no un código con formato propio.

## 5. Generar autorización (RF03/RF04, Expediente)

- **Lee**: `personas` (datos del cliente para el PDF), `numeradores` (clave `AUTORIZACION`, con
  `FOR UPDATE`).
- **Escribe**: `numeradores` (incrementa `ultimo_valor` para la clave `AUTORIZACION`/el año
  actual); `auditoria_operaciones` (no hay una tabla propia de "autorizaciones" — el número
  generado se guarda como `valor_nuevo` de la fila de auditoría,
  `ServicioAuditoria.registrarCambioDeValor`). **No se guarda ningún PDF** en este paso — el
  documento se genera recién cuando se abre para imprimir (ver más abajo).
- **Reglas de negocio**: `ServicioConsultaCrediticia.generarAutorizacionBuro` — transacción
  propia, numerador + auditoría van juntos o no va ninguno.
- **Auditoría**: `GENERAR_AUTORIZACION_BURO` ("Generar autorización de consulta al buró"),
  entidad PERSONA, con `valor_nuevo` = el número `AUT-aaaa-NNNNN` y el detalle con el nombre del
  cliente.
- **Archivos**: ninguno guardado — "Abrir para imprimir" (`GET
  /clientes/{id}/autorizacion-buro`) regenera el PDF al vuelo cada vez, confirmado por un
  comentario explícito del propio código ("sin tocar numeradores ni auditoria_operaciones").
- **Numeración**: `AUT-aaaa-NNNNN` (5 dígitos) — tabla `numeradores`, clave `AUTORIZACION`.

## 6. Cargar autorización firmada (RF03/RF04, Expediente)

- **Lee**: `auditoria_operaciones` (confirmar que la autorización se generó HOY,
  `autorizacionGeneradaHoy` — si no, rechaza con "Esta autorización es de otro día…").
- **Escribe**: `archivos_binarios` (el escaneo, `ArchivoBinarioDAO.insertar`),
  `documentos_expediente` (una fila nueva que referencia ese archivo),
  `consentimientos_crediticios` (`ServicioConsultaCrediticia.registrarConsentimiento`).
- **Reglas de negocio**: "mismo día" (arriba); el resto es el mecanismo común de carga de
  documentos (extensión + firma real de los primeros bytes + tamaño máximo,
  `ServicioDocumentos.validarArchivo`).
- **Auditoría**: `CARGAR_DOCUMENTO` ("Cargar documento del cliente"), entidad
  DOCUMENTO_EXPEDIENTE, por el escaneo cargado; `REGISTRAR_CONSENTIMIENTO` ("Registrar
  consentimiento crediticio"), entidad CONSENTIMIENTO_CREDITICIO.
- **Archivos**: el escaneo **no se guarda en disco** — queda completo como `BYTEA` en
  `archivos_binarios.contenido`, dentro de la base (`bd/01_esquema.sql:243`). Así se guardan
  todos los documentos del sistema, sin excepción.
- **Numeración**: ninguna nueva (reutiliza el número ya generado en el paso anterior).

## 7. Cargar documentos — cédula, comprobante de ingreso, informe de Informconf (RF03/RF04)

- **Lee**: `tipos_documento` (catálogo, para saber a qué tipo corresponde cada carga).
- **Escribe**: `archivos_binarios` + `documentos_expediente` por cada archivo (mismo mecanismo
  que el paso anterior).
- **Reglas de negocio**: `ServicioClientes.CODIGOS_EXPEDIENTE_OBLIGATORIOS` — hoy la única lista
  (cédula, comprobante de ingreso, autorización de buró) que decide qué cuenta para "Expediente:
  X de 3 documentos obligatorios" y para el gate real de "Enviar a aprobación" (paso 11); el
  comprobante de domicilio queda fuera de esa lista — **recordatorio: Joel decidió el
  21/10/2026 que pase a ser obligatorio, todavía sin implementar, ver `AVANCE.md`**.
- **Auditoría**: `CARGAR_DOCUMENTO` ("Cargar documento del cliente") por cada archivo (cédula,
  comprobante de ingreso, informe de Informconf), entidad DOCUMENTO_EXPEDIENTE.
- **Archivos**: ninguno en disco — mismo `BYTEA` en `archivos_binarios`.
- **Numeración**: ninguna.

## 8. Registrar consulta al buró (RF04)

- **Lee**: `parametros_sistema` (`buro.dias_vigencia_consentimiento`),
  `consentimientos_crediticios` (el último vigente, para vincular la consulta y validar que la
  autorización todavía no venció).
- **Escribe**: `consultas_crediticias` (`INSERT` con puntaje, clasificación, mora, resumen, y el
  documento del informe ya cargado).
- **Reglas de negocio**: `ServicioConsultaCrediticia.registrarConsulta`/
  `validarConsultaPermitida` — la consulta exige un consentimiento vigente (dentro de
  `buro.dias_vigencia_consentimiento`, mismo parámetro que la vigencia de 30 días que se ve en
  pantalla).
- **Auditoría**: `REGISTRAR_CONSULTA_BURO` ("Registrar resultado de consulta al buró"), entidad
  CONSULTA_CREDITICIA.
- **Archivos**: ninguno nuevo (el informe ya se cargó en el paso 7).
- **Numeración**: ninguna — el identificador es el `id_consulta_crediticia` autonumérico.

## 9. Crear solicitud (RF05)

- **Lee**: `solicitudes_credito`/`prestamos` (verificar que el cliente no tenga ya un compromiso
  abierto), `personas` (cliente activo), `personas_laborales` (ingreso, cuota de otras deudas,
  situación laboral), `personas_domicilio`+`ciudades` (departamento real, por JOIN),
  `parametros_sistema` (`credito.edad_minima`/`maxima`, `credito.departamentos_habilitados`,
  `credito.situaciones_laborales_admitidas`, `credito.monto_minimo`/`maximo`,
  `credito.plazo_minimo`/`maximo`, `formalizacion.dias_min`/`max_primer_vencimiento`,
  `credito.tea_empresa`, `credito.limite_afectacion`), `limites_tasa_usura`, `feriados`,
  `numeradores` (clave `SOLICITUD`).
- **Escribe**: `numeradores` (incrementa la clave `SOLICITUD`/año); `INSERT` en
  `solicitudes_credito` — `estado_solicitud = 'BORRADOR'`, con el código ya generado.
- **Reglas de negocio**: `ServicioSolicitudes.crear` → `validarPoliticaCredito` (edad/zona/
  situación laboral/comprobante, mismos parámetros que RF02) → `aplicarSimulacion` (límites de
  monto/plazo/fecha, tope de tasa, afectación ≤ 40 % — `credito.limite_afectacion`, sin
  excepción desde el 10/10/2026).
- **Auditoría**: `CREAR_SOLICITUD` ("Crear solicitud"), entidad SOLICITUD_CREDITO.
- **Archivos**: ninguno.
- **Numeración**: `SOL-aaaa-NNNN` (4 dígitos) — tabla `numeradores`, clave `SOLICITUD`,
  `ServicioSolicitudes.generarCodigo`.

## 10. Simular (RF05, dentro de la ficha de la solicitud en Borrador)

- **Lee**: `solicitudes_credito` (la fila actual), `personas`/`personas_laborales`, los mismos
  parámetros del paso 9.
- **Escribe**: `UPDATE` sobre la misma fila de `solicitudes_credito` (`actualizarSimulacion`) —
  monto, plazo, destino, fecha, cuota, TEA/TNA/CFT, afectación. **No crea una fila nueva.**
- **Reglas de negocio**: mismas de `aplicarSimulacion` que en "Crear solicitud".
- **Auditoría**: **ninguna** — es un recálculo, mismo criterio que la cotización de RF01 ("no
  guarda registros" en el sentido de eventos, aunque sí actualiza la fila).
- **Archivos**: ninguno.
- **Numeración**: ninguna nueva.

## 11. Enviar a aprobación (RF06)

- **Lee**: `solicitudes_credito`, `prestamos` (sin préstamo abierto), `personas`/
  `personas_laborales`, `documentos_expediente` (vía `agregarFaltantesExpediente` — hoy solo
  mira los 3 obligatorios del paso 7), `consultas_crediticias` (vía `agregarFaltantesBuro`, y
  para el bloqueo de mora RN-18), `parametros_sistema` (`credito.limite_afectacion`,
  recalculado con los datos actuales).
- **Escribe**: `UPDATE` en `solicitudes_credito` — `ingreso_cliente_evaluado`,
  `limite_afectacion_aplicado`, `porcentaje_afectacion` (el snapshot recalculado) y
  `estado_solicitud`: **`BORRADOR` → `PENDIENTE_APROBACION`** (`solicitudDAO.marcarEnviada`);
  `UPDATE` en `consultas_crediticias` (`vincularASolicitud`, pone el `id_solicitud` de esta
  solicitud en la consulta del cliente).
- **Reglas de negocio**: `ServicioSolicitudes.enviarAAprobacion` — revalida TODO de nuevo en el
  servidor (expediente, política de crédito, afectación ≤ 40 % con datos actuales, autorización/
  consulta al buró vigentes, sin mora en Informconf — RN-18, bloqueo directo sin excepción). Si
  falta algo, junta todos los puntos en un solo mensaje; no guarda nada hasta que todo esté
  completo.
- **Auditoría**: `ENVIAR_SOLICITUD_APROBACION` ("Enviar solicitud a aprobación"), entidad
  SOLICITUD_CREDITO.
- **Archivos**: ninguno.
- **Numeración**: ninguna nueva.

## Resumen: archivos en disco

**Ninguno de los pasos de esta escena guarda un archivo en el disco del servidor.** Todos los
documentos del expediente (cédula, comprobante de ingreso, autorización firmada, informe de
Informconf) se guardan completos como `BYTEA` dentro de `archivos_binarios.contenido`, en la
base de datos (confirmado en `bd/01_esquema.sql`). Los PDF que no se cargan sino que el sistema
genera (cotización, papel de requisitos, autorización para imprimir) tampoco se guardan en
ningún lado — se regeneran al vuelo en cada clic.

## Resumen: numeraciones generadas

Ambas vienen de la misma tabla `numeradores` (clave + año + `ultimo_valor`, incrementado con
`FOR UPDATE` para que dos pedidos simultáneos nunca repitan un número):

| Código | Formato | Clave en `numeradores` | Dónde se genera |
|---|---|---|---|
| `AUT-aaaa-NNNNN` | 5 dígitos | `AUTORIZACION` | `ServicioConsultaCrediticia.generarAutorizacionBuro` (paso 5) |
| `SOL-aaaa-NNNN` | 4 dígitos | `SOLICITUD` | `ServicioSolicitudes.generarCodigo` (paso 9) |

## Verificado (22/10/2026, resuelve los dos puntos que quedaban "a confirmar")

- **`tipos_documento` sí tiene** un campo booleano (`obligatorio_desembolso`, `bd/01_esquema.sql`)
  — pero es para el checklist del **desembolso** (RF13/RF16: contrato, pagaré y plan de pagos
  firmados, ver `05_desembolso.md`), no para el Expediente de la Escena 01. La lista de
  obligatorios del Expediente sigue siendo solo el `Set` de Java
  `ServicioClientes.CODIGOS_EXPEDIENTE_OBLIGATORIOS` (paso 7, arriba), sin relación con esa
  columna — son dos listas de "obligatorio" completamente separadas, para dos etapas distintas
  del mismo préstamo.
- **`GeneradorInformesPdf.parametrosBase`** (`infraestructura/GeneradorInformesPdf.java`) lee:
  `empresa.razon_social`, `empresa.ruc`, `empresa.domicilio`, `empresa.telefono`,
  `empresa.whatsapp` (los 5 parámetros de la empresa, para el encabezado de cualquier PDF que lo
  use), más `USUARIO_EMISOR` (recibido como argumento, no es un parámetro) y `FECHA_EMISION`
  (`Reloj.ahora()`, tampoco un parámetro). Tanto el PDF de la cotización (paso 1) como el de
  requisitos (paso 2) vuelven a leer `empresa.telefono`/`empresa.whatsapp` por su cuenta encima
  de lo que ya trae `parametrosBase` — una lectura duplicada sin efecto (el mismo valor, dos
  veces), no un error.
