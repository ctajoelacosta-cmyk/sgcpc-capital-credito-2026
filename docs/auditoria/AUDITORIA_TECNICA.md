# Auditoría técnica completa del sistema

Decisión de Joel (25/10/2026), antes de cualquier cambio de código de la fase 2. **Documento de
solo lectura**: ninguna línea de código, plantilla, base ni configuración se modificó para
escribir esto. Todo `SELECT` se corrió contra `sgcpc_db_pruebas`, nunca `sgcpc_db`, como
`sgcpc_user`.

**Cómo leer este documento**: cada hallazgo tiene un código `Txx` propio (no se mezcla con la
numeración `Bxx`/`Cxx` del recorrido operativo). Cuando un `Txx` coincide con algo ya registrado
en `docs/recorrido/`, se cita el `B`/`C` correspondiente en vez de duplicar el análisis — un
`Txx` nuevo siempre aporta algo que el recorrido no tenía (una mirada técnica/de seguridad, no
solo de uso). Se trabajó **área por área, en el orden pedido, sin agentes en paralelo**, con un
commit al cierre de cada área.

**Convención de columnas**: *Severidad* usa Crítica/Alta/Media/Baja, según el riesgo real para
el negocio (plata, datos de clientes, continuidad) — no según cuánto código haría falta tocar.
Cuando el comportamiento real en pantalla no se pudo confirmar (solo lectura de código, sin
poder reproducir todas las ramas con los datos de prueba disponibles), el hallazgo queda
marcado **"requiere prueba en pantalla"**, sin inventar qué pasaría.

---

## Área 1 — Seguridad

### Resumen del área

Revisado: `FiltroAutenticacion`, `FiltroAutorizacion`, `FiltroCsrf`, `ListenerSesion`, `Csrf`,
`ServicioAutenticacion`, `ServicioUsuarios`, `web.xml`, los 66 archivos `.jsp` de
`WEB-INF/vistas/`, las 11 consultas con `Statement` (SQL fijo) y las ~34 clases DAO con
`PreparedStatement`, y `ServicioDocumentos.validarArchivo`/`cargarDocumento`.

**Lo que ya está bien resuelto** (para no repetir en cada hallazgo): contraseñas con BCrypt
(`BCrypt.hashpw`/`checkpw`, nunca texto plano ni hash propio); mitigación explícita de canal
lateral de tiempo para no delatar si un usuario existe (`HASH_DUMMY_PARA_USUARIO_INEXISTENTE`);
un solo mensaje de error genérico para los 5 casos de login fallido (RNF09); bloqueo por
intentos fallidos con desbloqueo automático por tiempo; CSRF validado en **todo** POST, incluido
el propio login; **cero** inyección SQL explotable (toda consulta con datos variables usa
`PreparedStatement` parametrizado — las 11 consultas con `Statement` crudo son SQL fijo, sin
ningún dato de usuario, verificadas una por una); validación de archivos subidos por **contenido
real** (`detectarExtensionPorContenido`), no solo por extensión/MIME declarado, y sin ninguna
escritura a disco (todo el binario va a la base, así que no hay riesgo de path traversal);
páginas de error sin stacktrace ni versión del servidor (`web.xml`, `error-500.jsp` genérico);
sesión a los 30 minutos de inactividad; ninguna contraseña aparece en ningún log ni en ninguna
URL (formularios de login/cambio de contraseña, ambos por POST).

### Hallazgos

**T1 — Alta.** `GET /documentos/{id}` no valida que el documento pertenezca a un cliente/
préstamo que el usuario debería poder ver — solo exige el permiso general de la ruta
(`CARGAR_DOCUMENTOS_CONSULTA_BURO`, y para `GET` alcanza con `CONSULTA`, no hace falta `TOTAL`:
`FiltroAutorizacion.autorizaAcceso`). **Archivo/método**:
`ControladorDocumentosServlet.descargar` (línea 330) — llama a
`servicioDocumentos.obtenerPorId(conexion, idDocumento)` y entrega el archivo sin ningún chequeo
adicional de a quién pertenece ese documento. **Evidencia**: `documentos_expediente.id_documento`
es `bigint generated always as identity` (secuencial, fácil de enumerar); cualquier oficial o
gerente autenticado puede pedir `/documentos/2`, `/documentos/3`, etc. y recibir cédulas,
comprobantes de ingreso/domicilio o informes de buró de **cualquier** cliente, no solo los
propios. **Riesgo en lenguaje simple**: un empleado con acceso al sistema puede ver documentos
personales de clientes que nunca atendió, sin que quede ningún rastro (ni la vista ni la
descarga se auditan). **Recomendación**: agregar, en el mismo método, una verificación de que el
documento corresponda a una persona/préstamo que el perfil del usuario puede consultar (o, como
mínimo, auditar cada descarga con el id del documento y quién la pidió). **Ya registrado como
C19** (`00_LEEME.md`, área D/J de `FASE2_PLAN.md`) — este hallazgo confirma con código exacto el
mecanismo (`FiltroAutorizacion` línea 109-113, GET alcanza con `CONSULTA`) y agrega el dato de
que los IDs son secuenciales, sin repetir el análisis completo.

**T2 — Alta.** XSS almacenado: ningún `.jsp` de todo el proyecto usa una función de escape de
HTML (`c:out`, `fn:escapeXml`, `StringEscapeUtils`, o equivalente — búsqueda en los 66 `.jsp` de
`WEB-INF/vistas/`: cero coincidencias). Varios campos de **texto libre, escritos por un rol y
mostrados sin escapar a otro rol** se imprimen con `<%= ... %>` directo. **Evidencia concreta**
(archivo:línea): `reversiones/bandeja.jsp:89` (`motivoReversion`, escrito por la cajera, visto
por el gerente), `cobranza/ficha.jsp:147` (`motivoRechazoReversion`, escrito por el gerente,
visto por la cajera), `caja/ficha.jsp:430` (`motivoDiferencia`, cajera → gerente),
`clientes/ficha.jsp:235` (`detalleBaja`), `aprobaciones/ficha.jsp:76/82` (`motivoRechazo`/
`motivoCancelacion`), `solicitudes/ficha.jsp:84/90` (los mismos, del lado del oficial). Ninguno
de estos campos se valida más allá de un mínimo de caracteres (ej.
`ServicioReversiones.solicitarReversion` solo exige `motivo.trim().length() >= 10`, sin
restringir el contenido). **Riesgo en lenguaje simple**: una cajera (o cualquier rol con acceso
a un campo de motivo/observación) podría escribir, en vez de un motivo normal, un fragmento de
código que el navegador del gerente ejecutaría al abrir esa pantalla — y ese código corre
**como si fuera el gerente**, con su sesión y sus permisos. El token contra CSRF viaja en un
campo oculto del HTML (`plantilla/csrf.jsp`), visible para cualquier script que corra en la
página — así que ese código podría armar y enviar, en nombre del gerente, un POST real (por
ejemplo, autorizar una reversión) sin que él lo haya pedido. La cookie de sesión tiene
`HttpOnly` (confirmado en `web.xml`), así que no se puede robar directamente desde ese script —
pero igual se puede actuar en nombre de la víctima mientras tiene la pantalla abierta.
**Recomendación**: escapar sistemáticamente cualquier texto que no haya escrito el propio
sistema antes de imprimirlo en HTML (la forma más simple y pareja: una función de escape
centralizada, usada en los lugares donde hoy se imprime texto libre con `<%= %>` — contrato,
observaciones, motivos, nombres editables). No está registrado en el recorrido (es un hallazgo
técnico, no de uso) — no cita ningún B/C.

**T3 — Media.** Sin encabezados de defensa en profundidad: ninguna respuesta del sistema
agrega `X-Frame-Options` ni `Content-Security-Policy` (verificado: cero coincidencias en todo
`src/main/java`) — solo `ControladorDocumentosServlet` agrega `X-Content-Type-Options: nosniff`
al servir un documento (línea 346). **Riesgo en lenguaje simple**: sin `X-Frame-Options`, una
página externa podría mostrar el sistema dentro de un `<iframe>` invisible y inducir a un
usuario logueado a hacer clic sin saber qué está confirmando (clickjacking) — y, si se explotara
el T2, un `Content-Security-Policy` bien configurado habría limitado mucho el daño de ese script
inyectado (por ejemplo, bloqueando que llame a servidores externos). **Recomendación**: agregar
`X-Frame-Options: DENY` (o `SAMEORIGIN` si alguna pantalla legítima lo necesita) a todas las
respuestas, y evaluar un `Content-Security-Policy` básico más adelante, después de resolver T2.

**T4 — Baja, a confirmar con Joel.** La cookie de sesión tiene `HttpOnly` pero no `Secure`
(`web.xml`, `<cookie-config>`, solo declara `<http-only>true</http-only>`). **Riesgo en lenguaje
simple**: si el sistema alguna vez se accede por una red sin HTTPS (por ejemplo, Wi-Fi de la
oficina sin cifrar, o acceso remoto mal configurado), la cookie de sesión podría viajar en claro
y ser interceptada. **Requiere prueba en pantalla/confirmación de Joel**: no se pudo determinar
desde el código si el despliegue real usa HTTPS interno o es solo HTTP en una LAN controlada —
sin ese dato no corresponde subir la severidad ni bajarla. **Recomendación**: si hay HTTPS
disponible en el despliegue real, agregar `<secure>true</secure>` al `<cookie-config>`.

---

## Área 2 — Base de datos

### Resumen del área

Revisado: `bd/01_esquema.sql` (1008 líneas, 33 tablas), `bd/02_indices.sql` (161 líneas:
índices, triggers), `bd/03_parametros.sql` (357 líneas: parámetros y datos semilla),
`bd/recrear_base.sh`, `bd/migraciones/` (19 archivos históricos), `NumeradorDAO`, y los `\d` de
varias tablas contra `sgcpc_db_pruebas` (ya corridos en rondas anteriores de este proyecto para
`prestamos`, `cuotas`, `planes_pago`, `movimientos_caja`, `sesiones_caja`,
`documentos_expediente`, `tipos_documento`, `numeradores`, `auditoria_operaciones` — todos
coinciden exactamente con lo que el código espera).

**Lo que ya está bien resuelto**: 33 tablas, **79** referencias `REFERENCES` (FK), **26**
restricciones `UNIQUE` y **41** restricciones `CHECK` — densidad de integridad alta para el
tamaño del esquema. `bd/recrear_base.sh` corre **solo** `01_esquema.sql` + `02_indices.sql` +
`03_parametros.sql` sobre `sgcpc_db_pruebas` (nunca contra `sgcpc_db`, con una guarda explícita
que aborta si `bd.properties` no apunta a `_pruebas`) — es decir, esos 3 scripts son la fuente
de verdad **ya consolidada** (los 19 cambios de `bd/migraciones/` ya están plegados ahí, no hay
que sumarlos a mano); `bd/comparar_esquemas.sh` existe para que Joel mismo confirme que
`sgcpc_db` nunca se desvió a mano de esos scripts (no se corrió en esta auditoría: requiere
conectarse a `sgcpc_db`, fuera de lo permitido). **Numeradores** (`NumeradorDAO.siguienteValor`):
`SELECT ... FOR UPDATE` + `INSERT ... ON CONFLICT DO NOTHING` + `UPDATE ... SET ultimo_valor =
ultimo_valor + 1` — bloqueo de fila real, sin ventana para que dos cobros simultáneos se lleven
el mismo número de recibo/factura/constancia. **Doble envío** (doble clic o reintento):
`pagos.identificador_confirmacion`, `desembolsos.identificador_confirmacion` y
`reversiones_pago.identificador_confirmacion` son **`UNIQUE` a nivel de base**, no solo una
verificación en Java — un reintento con la misma clave nunca inserta dos veces (una violación de
unicidad se trata como éxito idempotente, confirmado leyendo el código); `generarDocumentos`
(Formalización) usa el mismo patrón apoyándose en `prestamos.id_solicitud UNIQUE`, sin necesitar
una columna de confirmación propia. **Inmutabilidad financiera**: 11 tablas (`prestamos`,
`cuotas`, `planes_pago`, `pagos`, `aplicaciones_pago`, `desembolsos`, `reversiones_pago`,
`movimientos_caja`, `sesiones_caja`, `facturas`, `liquidaciones_anticipadas`) tienen un trigger
`BEFORE DELETE` (`fn_bloquear_eliminacion`) que **rechaza cualquier intento de borrado a nivel
de base** (no solo porque el DAO no tenga un método `eliminar`) — y `auditoria_operaciones`
bloquea además cualquier `UPDATE`. Esto es una protección real incluso ante un error de
programación futuro o un acceso directo a la base.

### Hallazgos

**T5 — Baja.** `documentos_expediente` y `archivos_binarios` (los documentos del expediente y
sus archivos) **no** están en la lista de 11 tablas con el trigger `BEFORE DELETE`
(`bd/02_indices.sql`, línea 139-143), aunque la misma regla de negocio que protege a las tablas
financieras — "nunca se borra, se versiona" — también aplica a estos documentos (confirmado:
`cargarDocumento` reemplaza el vigente anterior marcándolo, nunca lo borra, y ninguna clase del
proyecto tiene un método `eliminar`/`DELETE` para estas dos tablas). **Evidencia**: búsqueda de
`DELETE FROM`/`.delete(` en las ~34 clases DAO — ningún resultado para `DocumentoExpedienteDAO`
ni `ArchivoBinarioDAO` (solo aparece en dos DAOs no relacionados, de referencias/cónyuge de
clientes). **Riesgo en lenguaje simple**: hoy no hay ningún botón ni método que borre un
documento de un cliente (cédula, comprobante, informe de buró) — pero esa garantía depende de
que nadie escriba ese método más adelante por error; en las tablas financieras, la misma
garantía existe aunque alguien lo intente. **Recomendación**: si se quiere la misma garantía
dura, extender el arreglo de tablas del trigger (línea 139-143 de `bd/02_indices.sql`) para
incluir `documentos_expediente` y `archivos_binarios` — cambio de una sola línea, sin tocar
ningún DAO ni servicio.

**Sin hallazgos adicionales de severidad Media/Alta/Crítica en esta área.** Un muestreo de
columnas de varias tablas (`personas`, `archivos_binarios`, `desembolsos`) contra su uso real en
el código no encontró ninguna columna sin ningún lugar que la lea o la escriba (ejemplo
verificado: `personas.fecha_vencimiento_cedula`, que a simple vista podría parecer sin uso,
aparece en 7 archivos distintos). El único comentario de un parámetro marcado "no se usa
todavía" (`bd/03_parametros.sql`, `formalizacion.dias_validez_documentos`) resultó estar
**desactualizado**: el parámetro sí se usa hoy, en `ServicioDesembolso`/
`ControladorDesembolsoServlet` — se registra en el Área 7 (comentarios desactualizados), no
acá, para no repetirlo dos veces.

## Área 3 — Lógica financiera

### Resumen del área

Revisado: `ServicioMotorFinanciero.java` (motor único de cálculo: plan de pagos, interés, mora,
IVA, TIR/CFT), `ServicioCobranza.java` (redondeo de efectivo, reconstrucción de líneas de
factura), `ServicioParametros.java` (límites de validación), `ServicioReportes.java` (tramos de
informes).

**Lo que ya está bien resuelto**: el IVA se calcula en **un solo lugar de todo el sistema**
(`ServicioMotorFinanciero.iva`, línea 61-62) — se confirmó que `ServicioCobranza` (las 2 únicas
otras clases que lo necesitan, en `reconstruirLineasFactura` y en el control de un adelanto)
**siempre delega** ahí, nunca recalcula el 10 % por su cuenta; mover el dato a un parámetro
(ver T6) sería, por esto mismo, un cambio de una sola línea. Todos los montos de dinero
(capital, interés, cuota, IVA) se calculan con `BigDecimal`, nunca con `double`/`float` — la
única excepción (ver T7) es un valor que no es dinero.

### Hallazgos

**T6 — Media.** El 10 % de IVA está **fijo en el código**, no en un parámetro del sistema:
`ServicioMotorFinanciero.java:42` (`TASA_IVA = new BigDecimal("0.10")`) y línea 43
(`UNO_MAS_IVA = new BigDecimal("1.10")`, la misma tasa escrita una segunda vez en vez de
derivarse de `TASA_IVA`). **Evidencia**: a diferencia de **todos** los demás datos fiscales del
sistema (timbrado, establecimiento, punto de expedición, rangos de numeración — todos en
`parametros_sistema`, editables sin tocar código, ya verificados en el recorrido), el IVA no
tiene ningún parámetro equivalente. **Riesgo en lenguaje simple**: si el Estado cambia la tasa
de IVA (ya pasó antes en Paraguay con otros impuestos), hace falta modificar y recompilar el
código para seguir facturando correcto — no alcanza con que Joel o el administrador cambien un
valor en Parámetros. **Ya anotado como observación sin numerar** en
`06_comprobantes_recibo_factura.md` § 9 punto 3 (y en `FASE2_PLAN.md`) — este hallazgo agrega la
ubicación exacta (línea 42-43) y confirma que es un cambio aislado y de bajo riesgo (un solo
punto de lectura en toda la base de código). **Recomendación**: mover `TASA_IVA` a
`parametros_sistema` (mismo patrón que `fiscal.iva_intereses`, que ya existe como parámetro pero
hoy no se usa para calcular — solo se muestra/documenta) y derivar `UNO_MAS_IVA` de ese valor en
vez de escribirlo aparte. De paso, revisar `ServicioParametros.TOPE_PUNITORIO` (línea 37, 30 %
fijo) — un caso más leve, porque es solo un techo de **validación** de un parámetro que sí es
configurable (`credito.porcentaje_punitorio`), no un dato que se usa directo en ningún cálculo.

**T7 — Baja.** El cálculo de la TIR/CFT (tasa interna de retorno, el costo financiero total que
se muestra al cliente) usa `double` con el método de bisección (`ServicioMotorFinanciero.java`,
líneas 189-208: `tirMensual`, `valorPresenteFlujos`) y `Math.pow` sobre `double` para una
potencia fraccionaria (línea 67, con un comentario propio del código explicando por qué:
`BigDecimal` no tiene una función de potencia con exponente fraccionario). **Riesgo en lenguaje
simple**: es la única cuenta de todo el motor financiero que no usa el tipo de dato exacto que
se usa para el resto del dinero — pero el resultado nunca se usa para calcular cuotas, capital
ni ningún monto que se cobre o se facture, solo para **mostrar** el CFT/TEA/TNA informativo (un
número con error de redondeo del orden de `10⁻¹⁵`, muchísimo más chico que el decimal que
realmente se muestra en pantalla). **Recomendación**: ninguna acción necesaria — se deja
registrado para que quede claro que es una decisión de diseño razonable (TIR es, por
definición, un cálculo iterativo sin solución exacta) y no un descuido.

**Ya registrado, sin repetir el análisis**: el redondeo de efectivo a favor del cliente
(`ServicioCobranza.calcularRedondeoEfectivo`, D-141/SEDECO) y el redondeo de los montos de
interés/IVA del motor (`ServicioMotorFinanciero.redondear`, línea 52) son **dos implementaciones
distintas para dos conceptos distintos** (denominación de billetes vs. unidad de moneda) que
comparten la palabra "redondeo" — ya anotado como observación 5 de `06_cobro_mapa_completo.md`
§ 13, sin clasificar A/B; esta auditoría no encontró ningún caso donde las dos implementaciones
calculen la **misma** cosa de dos formas distintas (no es una duplicación de lógica, es una
coincidencia de nombre). La diferencia entre mes fijo del plan de cuotas (`generarPlanPagos`) y
el prorrateo por días reales de un anticipo/cancelación (**D-156**, decisión ya tomada por
Joel el 22/10/2026 de **no** unificarlos) tampoco es un hallazgo nuevo — ya está documentada y
cerrada.

## Área 4 — Documentos PDF

### Resumen del área

Revisado: los 43 `.jrxml`/`.jasper` de `src/main/resources/reportes/`,
`herramientas/compilar_reportes.sh`, `ReportesCompiladosTest.java`,
`herramientas/reportes_jasper.sha256`, y el patrón `obtenerOGenerarPdf` en
`ServicioCobranza`/`ServicioCaja` (2 únicas clases que lo usan).

**Lo que ya está bien resuelto — y es la pregunta central de esta área**: *"¿cada `.jrxml`
tiene su `.jasper` sincronizado?"* **Sí, y no depende de que alguien se acuerde de
verificarlo**: `ReportesCompiladosTest.todosLosJasperCorrespondenASuJrxmlActual` compara el hash
SHA-256 de cada uno de los 43 `.jrxml` contra el guardado en `reportes_jasper.sha256` (escrito
la última vez que se compiló) — si no coinciden, **la prueba falla con el nombre exacto del
reporte desactualizado**. Esta prueba corre con cualquier `mvn test` (no hay que pedirla aparte,
confirmado leyendo el código: sigue el patrón `*Test` de Surefire) y las 776 pruebas, incluida
esta, pasaron en 0 fallas en la verificación ya hecha para esta misma auditoría (ver Área 8). En
otras palabras: **hoy, ahora mismo, los 43 pares están sincronizados**, con una garantía
automática y no solo una promesa de proceso.

El patrón de **PDF cacheado** (`obtenerOGenerarPdf`: genera una vez, guarda los bytes en una
columna propia, reutiliza en cualquier reimpresión — con un candado por operación para que dos
pedidos simultáneos no generen dos archivos) se usa en recibo/factura/constancia/nota de
anulación (`ServicioCobranza`) y en el arqueo de caja (`ServicioCaja`). Para recibo y factura
existe, además, un **bypass explícito**: si el pago está `REVERTIDO`, el código **no** usa el
PDF cacheado — llama de nuevo a `generarPdfRecibo`/`generarPdfFactura` para producir la versión
con la franja "ANULADO" (verificado en el código, líneas 1873 y 2134 de `ServicioCobranza.java`).

### Hallazgos

**Sin hallazgos nuevos — dos ya registrados, confirmados con evidencia de código exacta.**

- **C27** (`07_cierre_del_dia.md`, ya documentado como riesgo sin clasificar A/B): el PDF del
  arqueo **no** tiene ningún bypass equivalente al de recibo/factura — `ServicioCaja.java:496`
  llama a `obtenerOGenerarPdf` sin ninguna condición previa que compruebe si
  `sesiones_caja.gerente_revisa_id` cambió después de generarse. **Confirmación técnica nueva**:
  no es que el mecanismo de bypass no exista en el sistema (sí existe, y se usa para
  recibo/factura) — es que, específicamente para el arqueo, nadie lo conectó a
  `marcarDiferenciaRevisada`. Es, literalmente, el mismo patrón que ya resuelve este tipo de
  problema en otro lado, aplicado a un solo caso y no al otro.
- **C3/C12** (`04_formalizacion.md`/`05_desembolso.md`, familia "fecha elegida vs. fecha
  efectiva"): ya documentado que el contrato y el comprobante de desembolso pueden mostrar una
  fecha distinta de la que el plan de pagos real usa, cuando la fecha elegida cae en un día no
  hábil. No se encontró ningún caso **nuevo** de esta misma familia (misma dato, dos fuentes)
  en los demás 41 reportes revisados.
- **B58** (criterio de original/copia inconsistente entre recibo/factura/comprobante de
  desembolso): confirmado otra vez al revisar los pies de página de los 43 `.jrxml` — sin
  agregar ningún caso nuevo a los ya citados.

## Área 5 — Auditoría y trazabilidad

### Resumen del área

Revisado: `EtiquetasAuditoria.java` (62 operaciones con nombre legible), los ~52 códigos de
operación realmente usados en los `servicioAuditoria.registrar*` de todo `src/main/java`, y los
puntos ya señalados del recorrido sobre qué se audita y qué no.

**Lo que ya está bien resuelto**: cobertura amplia y consistente de los eventos de seguridad
(los 9 resultados posibles de un intento de acceso, los 2 cierres de sesión distintos del
voluntario, el vencimiento por inactividad, los 5 motivos de bloqueo/desbloqueo de cuenta) y de
**todas** las operaciones que mueven dinero o cambian el estado de un préstamo (cobro, anticipo,
cancelación total, reversión solicitada/autorizada/rechazada, desembolso, apertura/cierre de
caja, revisión de diferencia) — ninguna de ellas quedó sin su propio código de auditoría al
revisarlas. `comoEtiquetaLegible` nunca falla ante un código nuevo sin mapear (devuelve el
código crudo) — una desprolijidad posible (un código sin etiquetar) nunca se convierte en un
error.

### Hallazgos

**Sin hallazgos nuevos — dos ya registrados, confirmados con el inventario completo de
operaciones auditadas (no solo los 2-3 casos puntuales ya citados).**

- **B23** (confirmado de nuevo, ahora contra las **62** etiquetas completas, no solo una
  muestra): `IMPRIMIR_COMPROBANTE_DESEMBOLSO` y `REIMPRIMIR_COMPROBANTE_DESEMBOLSO`
  (`ServicioDesembolso.java:363`) siguen siendo las **únicas 2** de todo el sistema sin entrada
  en `EtiquetasAuditoria` — confirmado comparando la lista completa de códigos realmente usados
  contra las 62 etiquetas, no solo revisando a ojo.
  **CERRADO el 01/11/2026 — ya estaba resuelto desde el 25/10/2026**, commit `99a77b3` (*"B23:
  nombre legible en auditoria para el comprobante de desembolso"*): `EtiquetasAuditoria:79-80`
  mapea las dos, `auditoria.jsp:142` las usa (`comoEtiquetaLegible`) y hoy hay **64** operaciones
  etiquetadas, con `EtiquetasAuditoriaTest`. El **código** de la operación no se renombró (es dato
  de auditoría, nunca se toca): solo se le dio un nombre legible. La fila quedó abierta por
  descuido en este documento y en `FASE2_PLAN.md`.
- **Dato sin numerar, ahora con evidencia exacta** (ya anotado en
  `06_comprobantes_recibo_factura.md` § 9 punto 2): la propia reimpresión de un documento
  **solo se audita para el comprobante de desembolso** — es, literalmente, el **único** lugar de
  todo `src/main/java` donde aparece la palabra `"REIMPRIMIR"` en un código de operación.
  Recibo, factura, constancia de cancelación, constancia de devolución, constancia de
  reversión, nota de anulación y los 41 informes PDF/CSV: ninguno audita su propia
  reimpresión/reexportación (solo su **primera** emisión, vía `EMITIR_INFORME_PDF`/
  `EXPORTAR_CSV`/el código de operación propio del documento). Relacionado con **C19**
  (visualización/descarga de `/documentos/{id}`, ya citado como **T1** en el Área 1): ambos son
  la misma idea — "ver o volver a imprimir algo ya no deja el mismo rastro que crearlo la
  primera vez" — en dos partes distintas del sistema.

## Área 6 — Manejo de errores y robustez

### Resumen del área

Revisado: las 4 páginas de error de `web.xml` (403/404/500/`ErrorConexionBD`),
`error-500.jsp`/`error-bd.jsp`, los bloques `catch` de todo `src/main/java` (patrón
`catch (RuntimeException | SQLException) { rollback(); throw; }` ya visto en todos los
servicios transaccionales), y una búsqueda específica de llamadas a internet desde el
servidor (requisito "debe funcionar sin conexión").

**Lo que ya está bien resuelto**: **cero** llamadas salientes a internet en todo el código del
servidor — se buscaron clientes HTTP (`HttpClient`, `HttpURLConnection`, `okhttp`,
`RestTemplate`, cualquier variante): ningún resultado. La "consulta al buró" (Informconf) es
**carga manual de un informe**, no una integración en vivo con ningún servicio externo — y el
frontend (Bootstrap, FontAwesome, SweetAlert2, jQuery) está **vendorizado** en
`src/main/webapp/recursos/` (las URLs que aparecen en esos archivos son solo comentarios de
licencia de las propias librerías, nunca una etiqueta `<script src="https://...">` que de
verdad pida algo a internet). **Conclusión de esta área: el sistema no depende de internet para
nada de lo que hace, en ningún flujo revisado.** Páginas de error sin stacktrace, sin SQL, sin
nombre de clase ni versión de Tomcat (`error-500.jsp`, confirmado línea por línea). **Cero**
bloques `catch (Exception ...)` genéricos en todo el proyecto — todos los `catch` son de un tipo
específico (`SQLException`, `NegocioException`, o la combinación
`RuntimeException | SQLException` para deshacer una transacción). Los pocos bloques `catch`
vacíos que existen (manejo de una fecha mal escrita a mano en una URL, UTF-8 que la JVM
garantiza, un perfil inválido que debe quedar en `null` a propósito) tienen, cada uno, un
comentario que explica por qué no hace falta ninguna acción — no es una omisión, es una
decisión documentada.

### Hallazgos

**Sin hallazgos nuevos.** No se encontró ningún mensaje de error que exponga detalles técnicos
(SQL, nombres de clase, stacktrace) al usuario final, ninguna dependencia de internet, y ningún
patrón de manejo de excepciones descuidado.

## Área 7 — Calidad del código

### Resumen del área

Revisado: tamaño de los 10 archivos `.java` más grandes del proyecto, longitud de los métodos
más largos de `ServicioCobranza.java` (el archivo más grande, 2542 líneas), búsqueda de
marcadores `TODO`/`FIXME` y de código comentado (bloques de código muerto dejados "por si
acaso") en todo `src/main/java`, y el comentario desactualizado ya encontrado en el Área 2.

**Lo que ya está bien resuelto**: **cero** marcadores `TODO`/`FIXME`/`XXX` reales en todo el
proyecto (las únicas coincidencias de texto son la palabra de negocio "TODOS", de un filtro de
estado, nunca una tarea pendiente); **cero** bloques de código comentado (ningún `// if (...)`,
`// return ...` ni similar dejado como código muerto) — señal de una base disciplinada, sin
restos de versiones anteriores. Los nombres de clases/métodos/variables son consistentemente en
español, con el mismo criterio de un dominio a otro (`ServicioX`/`XDAO`/`ControladorXServlet`,
sin mezcla de idioma ni de convención).

### Hallazgos

**T8 — Media.** Varios métodos de `ServicioCobranza.java` son muy largos para revisar o
probar como una sola unidad: `generarPdfRecibo` (**250 líneas**), `confirmarCancelacionTotal`
(**226 líneas**, desde la línea 828), `confirmarCobro` (**205 líneas**, desde la línea 623),
`confirmarAnticipoCapital` (**179 líneas**, desde la línea 1054). **Riesgo en lenguaje simple**:
un método tan largo es más difícil de leer de punta a punta, más fácil de romper sin darse
cuenta al tocar una parte, y más difícil de cubrir con una prueba que use todas sus ramas.
**Matiz importante, para no proponer un cambio apurado**: las 3 funciones `confirmarX` son,
cada una, **una sola transacción financiera atómica** (validar → calcular → persistir → auditar
→ confirmar) — partirlas en métodos más chicos sin cuidado podría, paradójicamente, hacer más
fácil romper el "todo o nada" de la transacción, algo que este proyecto protege con cuidado en
todos los servicios (visto una y otra vez en las Áreas 1-6). **Recomendación**: no es urgente ni
de bajo riesgo tocarlo ahora — queda para cuando haga falta modificar alguna de estas 4 métodos
por otro motivo (ej. al resolver C23/C28, ya en el radar de la fase 2): ahí sí conviene evaluar
separar las partes de solo lectura/cálculo (que no tocan la base) de la parte transaccional,
sin cambiar el comportamiento.

**T9 — Baja.** Comentario desactualizado: `bd/03_parametros.sql`, línea 186-187, dice que
`formalizacion.dias_validez_documentos` **"no se usa todavía"** — confirmado (Área 2) que **sí**
se usa, en `ServicioDesembolso.java` y `ControladorDesembolsoServlet.java`. **Recomendación**:
actualizar el comentario para que no induzca a pensar que el parámetro es un dato sembrado sin
efecto real.

**Sin otros hallazgos**: no se encontraron inconsistencias de nombres relevantes para mantener
el sistema, ni duplicación de lógica fuera de lo ya explicado en el Área 3 (redondeo) — el
criterio de "cambio mínimo, sin parches" ya establecido en `docs/REGLAS_DESARROLLO.md` § 13
pesó en no proponer ninguna refactorización preventiva sin un motivo concreto que la justifique.

## Área 8 — Pruebas

### Resumen del área

Revisado: los 35 archivos `*Test.java` (776 métodos de prueba, `mvn test` corrido en esta misma
ronda de fase 2: **776/0/0/0, BUILD SUCCESS**), y `pruebas/humo_http.sh` (**5251 líneas**) —
**leído, no ejecutado** (ejecutarlo está en el `deny` permanente de esta etapa).

**La arquitectura de pruebas del proyecto tiene dos niveles, y hay que leerlos juntos para
responder bien "qué está cubierto"**:

1. **JUnit (776 pruebas, `mvn test`, segundos)**: solo métodos **puros** (sin `Connection`,
   `docs/REGLAS_DESARROLLO.md` § 7 punto 9) — el motor financiero completo (plan de pagos,
   mora/moratorio/punitorio con días de gracia y mora por tramos, IVA, TIR, redondeo), las
   validaciones de timbrado/numeración de factura, la construcción de líneas de factura, las
   reglas puras de reversión/caja/formalización, conversión de números a letras, formateo,
   etc. **Ninguna de las 776 toca la base de datos.**
2. **Humo (`pruebas/humo_http.sh`, minutos, requiere Tomcat + `sgcpc_db_pruebas` reales)**: todo
   lo que sí necesita persistencia real — login completo, los 3 caminos de cobro
   (`/cobranza/{id}/cobrar|anticipar|cancelar`), permisos por rol (403/302), y **cadenas
   completas de operaciones** armadas con variables propias (`ID_PRESTAMO_MORA`,
   `ID_PRESTAMO_TIMBRADO`, `ID_PRESTAMO_FINALIZACION`, `ID_PRESTAMO_R3_E9`...).

**Corrección importante a una afirmación ya registrada en el recorrido** (observación 3 de
`06_cobro_mapa_completo.md` § 13: *"el cobro normal de una cuota realmente vencida nunca se
probó en pantalla en todo el proyecto"*) — **sigue siendo cierto que nunca se probó EN
PANTALLA** (decisión de Joel de no simular fechas en el recorrido manual), pero **no es cierto
que esté sin probar**: `pruebas/humo_http.sh` tiene una función propia,
`vencer_cuota_hoy` (línea 3706 y otras), que fabrica una cuota vencida de verdad en la base de
pruebas y después cobra sobre ella por HTTP — con mora, con el límite del timbrado, y hasta
encadenando **cancelación total → cierre documental completo** para el escenario
`ID_PRESTAMO_R3_E9` (líneas 4879-4972, el mismo circuito que se recorrió hoy a mano con
`CC-2026-00002`). La factura aparece citada **107 veces** en el script.

### Hallazgos

**T10 — Media.** El flujo completo de **RF29 (cierre documental)** —
`ServicioFormalizacion.generarConstanciaDevolucion`, `.completarCierreDocumental`,
`PrestamoDAO.marcarFinalizado`/`.revertirFinalizacion` — tiene **cero** pruebas JUnit:
búsqueda de "CierreDocumental"/"ConstanciaDevolucion" en `ServicioFormalizacionTest.java` (y en
los otros 34 archivos de prueba): ningún resultado. **Su única cobertura automática es el
tramo de humo ya citado** (líneas 4879-4972) — que es real y end-to-end, pero es también el
nivel más lento y menos aislado: para probar, por ejemplo, qué pasa si se intenta completar el
cierre documental sin haber cargado el escaneo firmado, o generar la constancia dos veces
seguidas (idempotencia), hoy hace falta levantar Tomcat y la base completa, no alcanza con
`mvn test`. **Riesgo en lenguaje simple**: un cambio futuro en esta lógica (ej. al resolver C7,
"reemplazar un firmado") podría romper un caso puntual de cierre documental sin que ningún
`mvn test` lo avise — solo se notaría corriendo el humo completo (o, peor, en producción).
**Recomendación**: agregar pruebas JUnit de las partes puras de este flujo (ej.: ¿qué decide
`completarCierreDocumental` cuando falta el documento firmado? ¿es realmente idempotente
`generarConstanciaDevolucion` llamado dos veces? — mismo patrón de separar lo puro de lo
transaccional que ya usa el resto del proyecto, no un enfoque nuevo).

**Dato, no clasificado como T** (para que Joel lo confirme, no se pudo verificar en esta
auditoría de solo lectura): no se corrió `pruebas/humo_http.sh` — no se sabe, desde este
documento, si la última corrida real fue exitosa ni cuándo fue. Solo se confirmó **leyendo el
script** que los casos de mora/cobro vencido/cancelación/cierre documental están escritos ahí.

**Reflexión que vale la pena dejar escrita** (no es un hallazgo, es el motivo de ser de este
recorrido): el circuito completo de cancelación + cierre documental ya tenía cobertura de humo
de punta a punta desde antes de hoy — y aun así, **recorrerlo hoy a mano encontró 3 hallazgos
nuevos** (B74, B76, B77, B78 y el matiz de B3). Ningún script de `curl` puede notar que 3 pasos
aparecen "de a uno" sin avisar cuántos son, o que un botón de confirmación se parece demasiado a
otro — eso solo lo encuentra una persona mirando la pantalla. Es, en los hechos, la prueba de
que las pruebas automáticas y el recorrido manual cubren cosas distintas, no redundantes.

## Área 9 — Cumplimiento (RF01–RF40)

### Resumen del área

Esta área **no vuelve a analizar cada RF desde cero** — cruza la matriz RF → escena →
cumple/observación ya construida en `docs/recorrido/00_LEEME.md` (fruto de 9 días de recorrido
en pantalla) contra lo que esta auditoría encontró **leyendo el código** en las Áreas 1-8, para
dos cosas puntuales: (a) marcar dónde la matriz quedó vieja o incompleta, y (b) para los RF que
la matriz todavía no llenó (porque el recorrido en pantalla no llegó ahí), decir si el código
que los implementa **ya existe y parece completo**, aunque falte la prueba en pantalla.

### RF01–RF19 (tramos 1-4): sin novedad

Los 19 primeros RF ya tienen su fila completa en la matriz (`00_LEEME.md`), con hallazgos B/C
ya clasificados o derivados a una decisión — no se encontró, en el código revisado en las Áreas
1-8, ningún dato que contradiga esas filas. Dos notas menores de mantenimiento de la
documentación (no de cumplimiento del sistema):

- **RF09/RF10** ("Aprobar"/"Rechazar") tienen la celda "Escena/Cumple" vacía en la matriz,
  aunque están cubiertos en los hechos dentro de la fila de RF07/RF08 (`03_aprobacion.md`) — es
  un hueco de la tabla, no de la funcionalidad.
- **RF19** (cierre con arqueo) sigue marcado "No recorrido en pantalla todavía" en la matriz,
  pero `07_cierre_del_dia.md` (tramo 6) ya lo recorrió por completo después de esa fila
  escribirse — la fila de la matriz quedó desactualizada, mismo tipo de hallazgo que **T9**
  (Área 7).

### RF20–RF29 (tramo 5 y cierre documental): la matriz está vacía, el código y el recorrido NO

Las filas de **RF20 a RF28** de la matriz están literalmente en blanco ("—" en Escena y en
Cumple) — pero **esto es un hueco de la tabla, no un vacío real**: las Áreas 3, 4, 5 y 8 de esta
auditoría, y `06_primer_cobro.md`/`06_comprobantes_recibo_factura.md` del recorrido, ya
documentan con lujo de detalle que estos 9 RF **cumplen**, con sus hallazgos B/C ya registrados
(RF20/lista de trabajo, RF21/deuda exigible, RF22/cobro, RF23/recibo-factura, RF24/gestiones,
RF25/reversión, RF26-28/cancelación-constancia). **RF29** (cierre documental) es la única de
este bloque que esta misma ronda de trabajo ya corrigió en la matriz (hoy, 25/10/2026, con
`Cumple` y sus hallazgos B76-B78). **Recomendación**: llenar las 8 filas restantes (RF20-RF28)
de la matriz con una referencia a `06_primer_cobro.md`/`06_comprobantes_recibo_factura.md` — es
trabajo de documentación, no de código, y no bloquea nada de la fase 2.

### RF30–RF40 (panel, informes, administración): cumplen en el código; falta el recorrido en pantalla

La matriz marca estos 11 RF sin completar (RF30 parcial, RF31-40 en blanco) porque el tramo 7
(informes) y la parte de administración del tramo 0 **nunca se recorrieron en pantalla** — pero
leer el código para las Áreas 1-8 de esta auditoría encontró evidencia concreta de que **la
funcionalidad detrás de cada uno ya existe, no es un hueco pendiente de programar**:

| RF | Qué pide el anteproyecto | Evidencia encontrada en esta auditoría |
|---|---|---|
| RF30 | Panel con indicadores | Ya "Cumple, en lo visto hoy" para el gerente (matriz); Área 1/5 confirman `ServicioPanel` existe y se audita todo lo que calcula |
| RF31 | Resumen financiero, mensual, cartera, proyección | `ServicioReportes.java` (1750 líneas) y 6 `.jrxml` propios (`informe_resumen_financiero`, `informe_resumen_mensual`, `informe_cartera`, `informe_proyeccion_fondos`...) — Área 4 confirmó los 43 `.jrxml`/`.jasper` sincronizados, estos incluidos |
| RF32 | Informes de operación y cobranza | `informe_cobros`, `informe_gestiones`, `informe_desembolsos`, `informe_vencimientos`, `informe_morosidad`, `informe_incobrables` — existen y están sincronizados (Área 4) |
| RF33 | Facturas del mes e IVA | `informe_iva.jrxml`, `informe_facturas_anuladas.jrxml` — ya con tag `rf33-probado` citado en `06_comprobantes_recibo_factura.md` § 8 (probado antes de esta auditoría) |
| RF34 | Estado de cuenta, reimprimir | `estado_cuenta.jrxml`/`estado_cuenta_historial.jrxml`/`estado_cuenta_gestiones.jrxml` — ya citado y usado como referencia en `06_primer_cobro.md` (C26) |
| RF35 | Filtrar por fecha, exportar PDF/Excel | `ExportadorCsvTest.java` existe (Área 8); `ControladorReportesServlet` (2127 líneas) tiene rutas de exportación |
| RF36 | Ingreso, cambio obligatorio de contraseña | **Verificado a fondo en el Área 1** de esta misma auditoría (BCrypt, bloqueo, RF36 ya con pruebas dedicadas) |
| RF37 | Administrador: ABM de usuarios | `ServicioUsuariosTest.java` (41 pruebas), `ControladorUsuariosServlet`, ruta `/usuarios` protegida (Área 1) |
| RF38 | Opciones según el perfil | **Verificado a fondo en el Área 1**: `ServicioAutorizacion`/`FiltroAutorizacion`, con pruebas propias |
| RF39 | Parámetros + historial de cambios | `ServicioParametrosTest.java` (55 pruebas); prácticamente todos los parámetros citados en las Áreas 1-8 de esta auditoría (`fiscal.*`, `credito.*`, `seguridad.*`) ya se leyeron de esta tabla, no del código |
| RF40 | Auditoría con filtros | **Verificada a fondo en el Área 5** de esta misma auditoría (62 operaciones con etiqueta, filtros, `EtiquetasAuditoriaTest`) — servida en `/reportes/auditoria` (dentro de `ControladorReportesServlet`, no una ruta `/auditoria` propia, a diferencia de lo que sugiere la nota de la matriz) |

**Conclusión de esta área**: no se encontró ningún RF del anteproyecto (RF01-RF40) sin su
funcionalidad implementada en el código. Las únicas "faltas" reales son de **recorrido en
pantalla** (tramo 7 e informes/administración, ya señalados como pendientes en
`00_LEEME.md`) y de **completar la matriz** (RF09/RF10/RF19/RF20-28, trabajo de documentación).
Ningún hallazgo nuevo de cumplimiento (`Txx`) en esta área — los riesgos reales ya encontrados
en el código (T1-T10) quedan en sus áreas correspondientes, no se duplican acá.

---

## Resumen ejecutivo

En simple: **el sistema está construido con mucho cuidado y eso se nota en casi todo lo que se
revisó** — contraseñas bien protegidas, control de acceso en dos capas, sin forma de inyectar
SQL, dinero que nunca se puede borrar ni duplicar por un doble clic, documentos que siempre
coinciden con su diseño, y ninguna dependencia de internet. De 10 hallazgos técnicos nuevos
(`T1`-`T10`), **ninguno es Crítico** y la mayoría son de bajo esfuerzo para corregir. Los dos
que sí importan de verdad son de seguridad: **cualquier empleado puede ver documentos
personales de clientes que no le corresponden** (T1, sin que quede rastro) y **un texto escrito
por un rol puede ejecutar código en la pantalla de otro rol** (T2, lo que en el peor caso
permitiría forzar acciones como si las hiciera otra persona). Ninguno de los dos es explotable
desde fuera de la empresa — ambos requieren ya estar adentro, como un empleado — pero los dos
merecen arreglarse antes de confiarle al sistema datos de clientes reales. El resto de los
hallazgos (base de datos, lógica financiera, documentos, auditoría, errores, calidad de código,
pruebas, cumplimiento) son mejoras concretas y acotadas, no riesgos que detengan nada. Sobre
cumplimiento: **los 40 RF del anteproyecto tienen su funcionalidad implementada en el código**
— lo que falta en varios de ellos es solo completar el recorrido en pantalla y la
documentación, no programar nada nuevo.

## Tabla de todos los T por severidad

| Severidad | Código | Resumen | Área |
|---|---|---|---|
| **Alta** | T1 | IDOR en `GET /documentos/{id}`, sin control de pertenencia (cita C19) | 1. Seguridad |
| **Alta** | T2 | XSS almacenado: ningún `.jsp` escapa texto libre entre roles | 1. Seguridad |
| **Media** | T3 | Sin `X-Frame-Options`/`Content-Security-Policy` | 1. Seguridad |
| **Baja** (a confirmar) | T4 | Cookie de sesión sin `Secure` | 1. Seguridad |
| **Baja** | T5 | `documentos_expediente`/`archivos_binarios` sin trigger de inmutabilidad | 2. Base de datos |
| **Media** | T6 | `TASA_IVA` (10 %) fija en el código, no en un parámetro | 3. Lógica financiera |
| **Baja** | T7 | TIR/CFT usa `double` — aislado, sin acción necesaria | 3. Lógica financiera |
| **Media** | T8 | 4 métodos muy largos (179-250 líneas) en `ServicioCobranza.java` | 7. Calidad del código |
| **Baja** | T9 | Comentario desactualizado en `bd/03_parametros.sql` | 7. Calidad del código |
| **Media** | T10 | Cierre documental (RF29) sin ninguna prueba JUnit | 8. Pruebas |

Sin hallazgos nuevos en las Áreas 4 (Documentos PDF), 5 (Auditoría), 6 (Errores/robustez) y 9
(Cumplimiento) — se confirmaron con evidencia de código los hallazgos ya citados del recorrido.

## Recomendado antes del 26/10 08:00

Integrando lo ya decidido (B13, B3/B31, B36, B23, diseño de factura) con lo crítico/de alto
valor y bajo riesgo de esta auditoría — nada de esto toca cobro ni comprobantes en su lógica de
cálculo:

1. **B13** — normalizar el domicilio (coma duplicada), ya aprobado en el primer lote de
   `FASE2_PLAN.md`.
2. **B3/B31** — desambiguar los botones "Cancelar [algo]" que colisionan con el "Cancelar"
   genérico de los diálogos (versión acotada, sin tocar `plantilla/pie.jsp`).
3. **B36** — concordancia singular/plural en las tarjetas del panel.
4. ~~**B23** — nombre legible en auditoría para `IMPRIMIR_COMPROBANTE_DESEMBOLSO`/
   `REIMPRIMIR_COMPROBANTE_DESEMBOLSO`~~ **HECHO el 25/10/2026**, commit `99a77b3` (cerrado en
   este documento el 01/11/2026 — ver el detalle arriba).
5. **Diseño de factura** — cerrar lo pendiente de diseño/legal ya identificado (revisión del
   contador/abogado de **C36**, la firma y aclaración del emisor que falta en `factura.jrxml`
   según `RF23_recibo_factura.md` § 10, y el texto de **B75** para una cancelación anticipada) —
   antes de emitir una factura real a un cliente real.
6. **T5** — agregar `documentos_expediente`/`archivos_binarios` al trigger de inmutabilidad
   (una línea en `bd/02_indices.sql`, mismo patrón que las 11 tablas ya protegidas).
7. **T6** — mover `TASA_IVA`/`UNO_MAS_IVA` a un parámetro del sistema (un solo punto de lectura
   en todo el código, cambio aislado y de bajo riesgo).
8. **T9** — corregir el comentario desactualizado de `dias_validez_documentos`.

**T1 y T2 (Alta) quedan fuera de esta lista corta a propósito**: son los hallazgos más
importantes de toda la auditoría, pero ninguno de los dos es un cambio de una línea — T1
necesita diseñar bien la verificación de pertenencia (o como mínimo auditar cada descarga) y T2
necesita una función de escape centralizada aplicada de forma consistente en varios `.jsp`.
Apurarlos para una fecha corta es más riesgoso que dejarlos para un bloque de trabajo propio,
dedicado y bien probado — pero **deberían ser lo primero de la fase 2 después de esta lista
corta**, antes de cualquier otra mejora de uso.

## Lo que conviene dejar para después

- **T2 (XSS)** y **T1 (IDOR en documentos)** — por lo ya explicado arriba: urgentes, pero
  merecen su propio bloque de trabajo, no un apuro de una noche.
- **T3** (sin `X-Frame-Options`/CSP) — natural hacerlo junto con T2, una vez que se decida el
  enfoque de defensa en profundidad.
- **T8** (métodos largos de `ServicioCobranza`) — dividir con cuidado, solo cuando haya un
  motivo real para tocar esos métodos (ej. al resolver C23/C28 en la fase 2), no como
  refactor preventivo.
- **T10** (pruebas JUnit del cierre documental) — agregarlas la próxima vez que se toque RF29,
  o como tarea aparte de "deuda de pruebas", sin apuro de fecha.
- **Completar la matriz RF→escena de `00_LEEME.md`** (RF09/RF10/RF19/RF20-28, Área 9) — trabajo
  de documentación, sin ningún riesgo ni urgencia.

## Pendientes de "requiere prueba en pantalla o confirmación de Joel"

- **T4** (cookie de sesión sin `Secure`): no se pudo determinar desde el código si el
  despliegue real de Capital Crédito usa HTTPS interno o es solo HTTP dentro de una LAN
  controlada — la severidad correcta depende de ese dato, que solo Joel puede confirmar.
