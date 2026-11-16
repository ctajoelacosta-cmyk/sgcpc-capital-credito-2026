# RF40 — Auditoría de operaciones, con filtros (RNF12)

> **Estado: en análisis (13/10/2026), con los 3 pendientes del §10 en curso (punto 8 de la tanda
> de usabilidad, 21/10/2026 -- ver AVANCE.md).** La sesión del 13/10 fue de solo lectura. Paso 1
> ("Sistema" en vez del usuario real): aplicado y probado en pantalla. Paso 3 (nombres legibles +
> buscador normalizado): aplicado y probado en pantalla. Paso 2 (PA-28, IP/navegador en todas las
> operaciones): aplicado y probado en pantalla -- los 3 pasos del punto 8 ya quedaron probados.

## 1. Qué exige el anteproyecto

RF40: "El sistema debe registrar en la auditoría las operaciones realizadas, con fecha, hora y
usuario, y permitir consultarlas con filtros."

RNF12: "El sistema no debe permitir borrar operaciones registradas. Las correcciones se hacen
por anulación o reversión, con motivo, autorización del gerente y registro en la auditoría."

## 2. Cómo funciona

Una sola tabla, `auditoria_operaciones` (`bd/01_esquema.sql:629-642`), append-only (sin ningún
`UPDATE` ni `DELETE` en el código — ver §6d): `id_usuario` (nullable, para un intento de acceso
con un usuario que no existe), `fecha_hora`, `operacion` (código interno, ej. `ALTA_CLIENTE`),
`tipo_entidad` (ej. `PERSONA`), `id_entidad`, `valor_anterior`/`valor_nuevo` (JSONB, solo para
los cambios de parámetro), `direccion_ip`, `navegador`, `usuario_escrito` (solo login),
`detalle` (texto libre).

`ServicioAuditoria` (`servicios/ServicioAuditoria.java`) es el único punto de escritura, con 4
variantes de `registrar(...)` según cuántos datos hay (con/sin IP, con/sin navegador) más
`registrarCambioDeValor(...)` (agrega `valor_anterior`/`valor_nuevo`) y
`registrarIntentoUsuarioInexistente(...)`. Cada servicio de negocio llama a una de estas DENTRO
de su propia transacción, antes del `commit` — si la operación de negocio falla, la auditoría
tampoco queda (atómico, nunca un registro de algo que no pasó).

La pantalla es `/reportes/auditoria` (`reportes/auditoria.jsp`, servida por
`ControladorReportesServlet`, método `mostrarAuditoria`) — ver §4.

## 3. Auditoría (de la auditoría misma)

Exportar el informe (PDF o CSV) de Auditoría queda auditado (`EMITIR_INFORME_PDF`/
`EXPORTAR_CSV`, entidad `INFORME_AUDITORIA`) — pero **cargar la pantalla en sí
(`mostrarAuditoria`) no queda registrado**: no hay forma de saber, mirando la propia auditoría,
quién solo miró la pantalla sin exportar nada. Mismo criterio que el resto de los informes (ver
§6a, fila "Exportaciones") — no es una falla de RF40 en particular, es el mismo patrón en todo
el sistema.

## 4. Dónde está

- Pantalla: `/reportes/auditoria` → `src/main/webapp/WEB-INF/vistas/reportes/auditoria.jsp`.
- Controlador: `ControladorReportesServlet.java` (métodos `mostrarAuditoria`,
  `generarInformeAuditoriaPdf`, `exportarAuditoriaCsv`, líneas ~1808-1893).
- Servicio de lectura: `ServicioReportes.listarAuditoria`/`contarAuditoria`/
  `contarTotalesPorOperacion`.
- Servicio de escritura: `ServicioAuditoria.java` (único punto de escritura de todo el sistema).
- DAO: `AuditoriaDAO.java`.
- DTO: `FilaAuditoria.java`, `UltimaOperacionAuditoria.java` (este último para la reutilización
  de consultas al buró vigentes, no para la pantalla de auditoría).
- Permiso real: `VER_REPORTES` (ver §6e — **no** `CONSULTAR_AUDITORIA`, que existe en el enum y
  en la matriz pero no lo lee ningún filtro ni controlador).

## 5. Dependencias

- Los ~25 servicios de negocio que auditan (ver tabla de §6a) — todos pasan por
  `ServicioAuditoria`, nunca escriben `auditoria_operaciones` directamente.
- `FiltroAutorizacion` (permiso `VER_REPORTES`) y `ControladorReportesServlet`
  (`SUBREPORTES_OFICIAL`, lista aparte dentro del controlador) — los dos filtros de acceso, ver
  §6e.
- `docs/funcionamiento/procesos/login.md` §10 — los 4 pendientes conocidos (ver §6b) ya estaban
  anotados ahí; este documento los hereda y los cita.

## 6. Análisis

### 6a. Cobertura — operaciones que modifican datos

| Operación | ¿Se audita? | Código(s) | ¿El detalle dice qué cambió? |
|---|---|---|---|
| Alta de cliente | Sí | `ALTA_CLIENTE` | Nombre y C.I., no el resto de los datos (es un alta, no hay "antes") |
| Edición de cliente | Sí | `ACTUALIZAR_CLIENTE` | **Sí** — `camposModificados` lista campo por campo lo que cambió |
| Baja/reactivación de cliente | Sí | `DAR_DE_BAJA_CLIENTE`, `REACTIVAR_CLIENTE` | Sí, con motivo |
| Reemplazo de documento | Sí | `CARGAR_DOCUMENTO` (1.ª vez) / `NUEVA_VERSION_DOCUMENTO` (reemplazo) | Sí — distingue alta de reemplazo, dice la versión nueva |
| Consentimiento/consulta al buró | Sí | `REGISTRAR_CONSENTIMIENTO`, `REGISTRAR_CONSULTA_BURO`, `OPERACION_GENERAR_AUTORIZACION_BURO` | Sí (proveedor, número de autorización) |
| Solicitud (creación) | Sí | `CREAR_SOLICITUD` | Código de la solicitud, no los datos simulados |
| Enviar a aprobación | Sí | `ENVIAR_SOLICITUD_APROBACION` | Sí |
| Aprobación | Sí | `APROBAR_SOLICITUD` | Dice que se aprobó, **no** el monto/TEA/plazo aprobado (quedan en la propia solicitud, no en el detalle) |
| Rechazo | Sí | `RECHAZAR_SOLICITUD` | Sí, con motivo |
| Cancelar solicitud | Sí | `CANCELAR_SOLICITUD` | Sí, con motivo |
| Formalización | Sí | `GENERAR_DOCUMENTOS_FORMALIZACION`, `REGENERAR_DOCUMENTOS_FORMALIZACION`, `GENERAR_PDF_FORMALIZACION` | Sí (operación, fecha de primer vencimiento) |
| Anulación (préstamo, antes del desembolso) | Sí | `ANULAR_PRESTAMO_PRE_DESEMBOLSO` | Sí, con motivo |
| Cierre documental | Sí | `COMPLETAR_CIERRE_DOCUMENTAL` | Sí |
| Apertura de caja | Sí | `ABRIR_CAJA` | Sí (saldo inicial) |
| Entrega de efectivo a caja | Sí | `ENTREGAR_EFECTIVO_CAJA` | Sí (monto) |
| Cierre de caja | Sí | `CERRAR_CAJA` | Sí (teórico, físico, diferencia) |
| Revisión de diferencia de caja | Sí | `REVISAR_DIFERENCIA_CAJA` | Sí, con observación |
| Desembolso | Sí | `DESEMBOLSAR` | Sí (monto neto) |
| Cobro | Sí | `COBRAR` | Sí (monto) |
| Cancelación total | Sí | `CANCELAR_TOTAL` | Sí (monto) |
| Adelanto a capital | Sí | `ANTICIPAR_CAPITAL` | Sí (monto) |
| Reversión (solicitar/rechazar/autorizar) | Sí | `SOLICITAR_REVERSION`, `RECHAZAR_REVERSION`, `AUTORIZAR_REVERSION` | Sí, con motivo en los 3 pasos (RNF12: motivo + autorización del gerente, cumple) |
| Gestión de cobranza (mora) | Sí | `REGISTRAR_GESTION_COBRANZA` | Sí |
| Recálculo de mora | Sí | `RECALCULAR_ESTADO_MORA` (por préstamo), `RECALCULO_MORA_DIARIO` (resumen) | **Sí** — dice el estado anterior → nuevo; pero el usuario que queda registrado no es una persona real (ver §6b) |
| Usuarios (alta/editar/bloquear/inactivar/reactivar/restablecer) | Sí | `ALTA_USUARIO`, `ACTUALIZAR_USUARIO`, `BLOQUEAR_USUARIO`/`INACTIVAR_USUARIO` (según corresponda), `REACTIVAR_USUARIO`, `RESTABLECER_CONTRASENA` | Sí — `ACTUALIZAR_USUARIO` lista los campos cambiados |
| Parámetros (cambiar valor) | Sí | `CAMBIAR_PARAMETRO` | Sí, desde RF39 2b (valor anterior, nuevo y motivo) |
| Feriados (alta/activar/desactivar) | Sí | `ALTA_FERIADO`, `ACTIVAR_FERIADO`, `DESACTIVAR_FERIADO` | Sí, desde RF39 E2 (antes → después) |
| Límites de tasa (alta) | Sí | `ALTA_LIMITE_TASA` | Sí, desde RF39 E2 (anterior → nuevo) |
| Exportaciones (PDF/CSV, ~19 informes distintos) | Sí, las 2 variantes de cada uno | `EMITIR_INFORME_PDF`/`EXPORTAR_CSV`, entidad `INFORME_<nombre>` | Dice los filtros aplicados (texto), no "qué cambió" (no corresponde — una exportación no cambia datos) |
| **Ver la pantalla de auditoría sin exportar** | **No** | — | — ver §3 |
| **Ver cualquier otra pantalla de informe sin exportar** | **No** | — | — mismo patrón, no es específico de auditoría |

**Ninguna operación de la lista de Joel quedó sin auditar.** Las únicas 2 filas marcadas "No"
son "ver una pantalla sin exportar", que ni Joel pidió ni el anteproyecto exige (RF40 habla de
"las operaciones realizadas", es decir las que cambian algo).

### 6b. Pendientes conocidos del LEEME (confirmados, no son hallazgos nuevos)

Los 4 ya estaban anotados en `docs/funcionamiento/procesos/login.md` §10 — esta sesión los
confirma leyendo el código, no los reinventa:

1. **Quién modifica datos de un cliente y quién reemplaza cada documento** — **ya está resuelto
   y no es un pendiente real**: `ACTUALIZAR_CLIENTE` guarda `idUsuarioQueModifica` y la lista de
   campos cambiados (`ServicioClientes.java:960-996`); `CARGAR_DOCUMENTO`/
   `NUEVA_VERSION_DOCUMENTO` guardan `idUsuarioQueCarga` y distinguen alta de reemplazo
   (`ServicioDocumentos.java:165-182`). La decisión del 09/10 (el gerente puede editar y
   reemplazar) no necesitó ningún cambio de código — ya quedaba registrado igual para cualquier
   perfil con permiso de editar.
2. **`RECALCULO_MORA_DIARIO` aparece como "Sistema"** — confirmado:
   `ServicioMora.java:75` registra la operación diaria con un `idUsuario` que no es una persona
   real (el primer usuario que entra ese día, según el comentario de `login.md`). Sin
   implementar.
3. **IP y navegador vacíos fuera del login** — confirmado: la variante de `ServicioAuditoria.
   registrar(...)` de 6 parámetros (la que usan todas las operaciones de negocio de la tabla de
   §6a) pasa `null` para IP/navegador; solo la variante de login los recibe. Requeriría pasar el
   `HttpServletRequest` a cada `ServicioXxx.operacion(...)`, que hoy no lo necesita. Sin
   implementar.
4. **Columna "Detalle" cortada** — confirmado en `reportes/auditoria.jsp`: la celda no envuelve
   el texto largo. El dato no se pierde (`detalle` es `TEXT`, sin límite). Sin implementar.

### 6c. Filtros de `/reportes/auditoria`

- **Filtro por usuario: el backend YA lo soporta, la pantalla NO lo muestra.**
  `ControladorReportesServlet.mostrarAuditoria` ya lee `request.getParameter("idUsuario")` y lo
  pasa a `listarAuditoria`/`contarAuditoria`/`contarTotalesPorOperacion`
  (líneas 1808-1833) — **pero `auditoria.jsp` nunca pinta un campo `name="idUsuario"`** (confirmado
  leyendo el archivo completo, solo hay 4 campos: Desde, Hasta, Entidad, Operación). Es una
  capacidad ya construida y nunca conectada a la pantalla — el arreglo, si Joel lo pide, sería
  agregar un `<select>` de usuarios al formulario, sin tocar ni el controlador ni el servicio.
- **"Entidad" es texto libre, sí** (`<input type="text" name="tipoEntidad" placeholder="ej.
  PRESTAMO">`, línea ~44 de la JSP) — el usuario tiene que saber y escribir el nombre interno
  exacto (`PERSONA`, `PRESTAMO`, `PAGO`, etc.). Hay 37 valores distintos en uso hoy (18
  entidades de negocio + 19 tipos de informe, `INFORME_*`) — una lista sería mejor, aunque larga;
  alternativa más simple: un `<datalist>` (autocompletar sin perder el texto libre) en vez de un
  `<select>` con 37 opciones.
- **"Operación contiene"** también es texto libre (`name="operacion"`) — mismo criterio, busca
  por coincidencia parcial del código interno (ej. "CANCELAR" encuentra `CANCELAR_SOLICITUD` y
  `CANCELAR_TOTAL`).
- **PDF y CSV sí respetan los filtros** (mismos `desde`/`hasta`/`idUsuario`/`tipoEntidad`/
  `operacion`, misma llamada a `listarAuditoria` que la pantalla) — confirmado leyendo
  `generarInformeAuditoriaPdf`/`exportarAuditoriaCsv`.
- **PDF y CSV NO traen las columnas nuevas** — confirmado: el CSV encabeza con "Fecha/hora;
  Usuario;Operación;Entidad;Id;Detalle" (6 columnas, `ControladorReportesServlet.java:1883`) y el
  PDF (`informe_auditoria.jrxml`) tiene las mismas 6 — ninguno de los dos trae "Usuario escrito",
  "IP" ni "Navegador", que la pantalla sí muestra (9 columnas) desde el 09-10/10/2026 (D-152).
  Coincide exactamente con lo que ya decía `login.md` §10.

### 6d. RNF12 — ningún DELETE de operaciones registradas

Búsqueda exhaustiva (`grep -rniE "delete[[:space:]]+from"` sobre todo `src/main/java/`): **cero
resultados**. Ningún DAO del sistema tiene un método `eliminar`/`borrar`, y no existe ningún
`DELETE FROM` en ningún archivo `.java`. Confirmado específicamente para préstamos, cuotas
(`pagos`), recibos/facturas, movimientos de caja y auditoría — ninguno tiene una vía de borrado.

**Corrección (13/10/2026) sobre la primera versión de este documento**: decía que no había
ningún `TRIGGER` que bloqueara un `DELETE` a nivel de base — **esto era un error**: solo se
había revisado `bd/01_esquema.sql`. El trigger sí existe, en `bd/02_indices.sql:108-147`
("Protección contra DELETE en tablas financieras", Doc. 00 Sec. 10.4): la función
`fn_bloquear_eliminacion()` (un `RAISE EXCEPTION`) está aplicada con `BEFORE DELETE` sobre 11
tablas (`prestamos`, `cuotas`, `planes_pago`, `pagos`, `aplicaciones_pago`, `desembolsos`,
`reversiones_desembolso`, `reversiones_pago`, `movimientos_caja`, `sesiones_caja`, `facturas`,
`liquidaciones_anticipadas`), y `auditoria_operaciones` tiene su propio par de triggers
(`fn_bloquear_modificacion_auditoria()`) que bloquean **tanto `DELETE` como `UPDATE`** — más
estricto que las otras 11 (Doc. 08 Sec. 8.5: "los registros de auditoría son de solo
inserción"). Todas las tablas que Joel nombró en el punto (d) (préstamos, cuotas, pagos,
facturas, movimientos de caja, auditoría) están cubiertas. La garantía de RNF12 **no depende
solo del código de la aplicación**: incluso una conexión directa con `psql` o un futuro bug con
un `DELETE` quedarían rechazados por la base. (Tablas fuera de esta lista — `personas`,
`usuarios`, `solicitudes_credito`, `documentos_expediente`, `parametros_sistema`, `feriados`,
`limites_tasa_usura` — no tienen este trigger; coherente con que esas no son "operaciones
financieras confirmadas", y ninguna de ellas estaba en la lista que pidió Joel.)

### 6e. Quién puede ver la auditoría

Dos mecanismos distintos, en capas distintas:

1. **`FiltroAutorizacion`** — ruta `/reportes` (que incluye `/reportes/auditoria`) exige
   `Permiso.VER_REPORTES` con nivel `!= NINGUNO` para `GET`
   (`FiltroAutorizacion.java:71`). Matriz (`ServicioAutorizacion.java`): gerente `TOTAL` (línea
   57), oficial `CONSULTA` (línea 97), cajero `NINGUNO` (línea 128), administrador `TOTAL`
   (automático, tiene `TOTAL` en todos los permisos sin excepción). **La cajera queda bloqueada
   acá mismo, con 403, antes de llegar al controlador.**
2. **`ControladorReportesServlet`** — dentro del controlador, un chequeo aparte SOLO para
   `OFICIAL_CREDITO`: `SUBREPORTES_OFICIAL` es una lista fija de 5 subreportes permitidos
   (`vencimientos`, `morosidad`, `gestiones`, `estado-cuenta`, `consultas-buro`) — "auditoria" NO
   está en esa lista, así que un oficial que SÍ pasó el filtro (tiene `CONSULTA`) recibe 403 igual,
   pero por este segundo chequeo (línea 79-82).

**Resultado: gerente y administrador pueden ver `/reportes/auditoria`; oficial y cajera reciben
403 — pero por mecanismos distintos** (cajera en el filtro, oficial en el controlador). Coincide
con lo que Joel esperaba.

**Hallazgo de esta sesión**: existe un permiso `Permiso.CONSULTAR_AUDITORIA` (enum y matriz
completos — gerente `TOTAL`, oficial/cajero `NINGUNO`, línea 64/100/131 de
`ServicioAutorizacion.java`) que **parece ser el permiso pensado para esto, pero no lo lee
ningún `FiltroAutorizacion` ni ningún controlador** — grep confirma que las únicas 4 apariciones
de `CONSULTAR_AUDITORIA` en todo `src/main/java/` son su propia definición y las 3 líneas de la
matriz. Es un permiso "fantasma": sus valores coinciden por casualidad con el resultado real
(que viene de `VER_REPORTES` + la lista `SUBREPORTES_OFICIAL`), pero si alguien lo cambiara
pensando que así se ajusta el acceso a la auditoría, no pasaría nada. Mismo patrón que
`VER_PANEL_REPORTES` (hallazgo de la sesión de RF38).

## 7. Decisiones

Ninguna — sesión de solo análisis, sin cambios de código ni de base de datos.

## 8. Historial de cambios

| Fecha | Acción | Commit | Tag |
|---|---|---|---|
| 13/10/2026 | Análisis de solo lectura de RF40 (cobertura, RNF12, filtros, permisos) contra el anteproyecto; creación de este documento y actualización de `LEEME.md` | `832e0fc` | — |
| 13/10/2026 | Corrección del análisis de RNF12 (el trigger anti-DELETE ya existe, `bd/02_indices.sql`) | `0ace4c7` | — |
| 13/10/2026 | Implementados F1 (filtro Usuario)/F2 (filtro Entidad)/F3 (PDF/CSV con las 3 columnas nuevas)/F4 (Detalle sin cortar); `mvn test` en verde. | — | — |
| 13/10/2026 | Encontrados y restaurados 11 `.jasper` con ruido de recompilación (ver método de REGLAS §13); agregado el modo de un solo reporte a `HerramientaCompilarReportes`/`compilar_reportes.sh` (REGLAS §6/§13). | `a331af5` (solo las 2 notas de REGLAS) | — |
| 13/10/2026 | F1-F4 y la herramienta de compilación probados en pantalla y aprobados por Joel, después de su Clean and Build (ver §9). | *(este commit)* | `rf40-probado` |

## 9. Pruebas realizadas en pantalla

Probado en pantalla por Joel el 13/10/2026, en `sgcpc_db_pruebas`, después de su Clean and
Build:

- **Los 11 `.jasper` restaurados no cambiaron nada**: recibo `REC-2026-000015` y factura
  `001-001-0000009` de `CC-2026-00028` salen iguales que antes — confirma que la restauración
  (§13) no tocó ningún diseño real.
- **F1/F2**: `/reportes/auditoria` (gerente) muestra las listas desplegables de Usuario y
  Entidad; filtrando por "Joel Jesús Acosta" + `PARAMETRO_SISTEMA` → 4 registros correctos.
- **F4**: el Detalle se ve completo, con salto de línea, sin scroll horizontal.
- **F3**: el PDF trae "Filtros aplicados" con el usuario y la entidad elegidos, los mismos 4
  registros, y las columnas Usuario escrito/IP/Navegador. El CSV: las mismas 4 filas y columnas.
- **Permisos**: oficial y cajera, al escribir `/reportes/auditoria` a mano → "Acceso
  restringido" (403, confirmado en pantalla — antes solo verificado leyendo el código).

## 10. Pendientes

De los 4 ya conocidos (§6b): ~~`RECALCULO_MORA_DIARIO` como "Sistema"~~ -- **resuelto (punto 8
paso 1, 21/10/2026), probado en pantalla** (de paso, `RECALCULAR_ESTADO_MORA` -- la operación por
préstamo dentro del mismo recálculo diario -- suma el mismo tratamiento, ver AVANCE.md); columna
"Detalle" cortada -- **resuelto, F4** (ver §9); IP/navegador vacíos fuera del login (PA-28, punto
11 abajo) -- **resuelto (punto 8 paso 2, 21/10/2026), probado en pantalla**. (El 4.º,
"quién modifica/reemplaza", ya estaba resuelto desde antes — ver §6b punto 1.)

0. ~~Nombres legibles de las operaciones (pantalla y totales) -- hoy se ve el código interno~~
   (pedido nuevo del 13/10/2026, `AVANCE.md`, no estaba en la lista original de este documento) --
   **resuelto (punto 8 paso 3, 21/10/2026), probado en pantalla**: tabla de 60 códigos
   (`infraestructura/EtiquetasAuditoria`), aplicada en la columna Operación, "Totales del
   período" y un `<datalist>` nuevo en el filtro "Operación contiene" (que además pasó a buscar
   por palabra en el nombre legible, normalizado sin mayúsculas ni acentos, no solo en el código
   crudo). PDF/CSV siguen sin tocar. Ver AVANCE.md para el detalle completo y la tabla.

5. ~~Filtro por usuario no expuesto en pantalla~~ — **resuelto (F1)**. Probado en pantalla.
6. ~~"Entidad" es texto libre~~ — **resuelto (F2)**, `<select>` con los 37 valores reales.
   Probado en pantalla.
7. ~~PDF/CSV de auditoría sin las 3 columnas nuevas~~ — **resuelto (F3)**. Probado en pantalla.
8. **Permiso `CONSULTAR_AUDITORIA` fantasma** — sigue solo documentado, sin cablear (decisión de
   Joel, 13/10/2026): definido y con valores coherentes en la matriz, pero no lo usa ningún
   filtro ni controlador; el acceso real depende de `VER_REPORTES` + `SUBREPORTES_OFICIAL`
   (§6e). Riesgo bajo (los valores hoy coinciden). No implementar sin pedido expreso.
9. ~~No hay ningún `TRIGGER` que bloquee un `DELETE`~~ — error de la primera versión de este
   documento, corregido en §6d. No queda nada pendiente acá.
10. Ver una pantalla de informe sin exportar nada no queda auditado (§3) — mismo patrón en todo
    el sistema, no específico de auditoría.

**Nuevos, de la prueba del 13/10/2026 (Joel: anotar, SIN implementar — se hace después de
cerrar los 40 RF, con prueba propia):**

11. **PA-28 — IP y navegador en todas las operaciones.** **Resuelto (punto 8 paso 2, 21/10/2026),
    probado en pantalla**: `infraestructura/ContextoPeticion` (`ThreadLocal<String>`
    para IP y navegador, nuevo) llenado por `FiltroAutenticacion.doFilter` al entrar cada
    petición (misma lectura que ya usa el login: `getRemoteAddr()`/`getHeader("User-Agent")`,
    sin helper propio) dentro de un `try` que arranca antes del primer `if`, con
    `ContextoPeticion.limpiar()` en un `finally` que envuelve el método entero (cualquiera de los
    4 `return` intermedios, o el camino normal) -- así nunca queda un valor pegado al hilo
    (Tomcat los reutiliza) para la operación de otro usuario, el único riesgo real. Lo leen
    `ServicioAuditoria.registrar` (6 parámetros, ~25 operaciones de negocio) y
    `registrarCambioDeValor` (`CAMBIAR_PARAMETRO`, `GENERAR_AUTORIZACION_BURO`), pasando
    `null`/`null` para las operaciones del sistema según
    `EtiquetasAuditoria.esOperacionDelSistema` (mismo método que ya decide la columna Usuario =
    "Sistema" del paso 1, centralizado en un solo lugar). Las variantes que ya recibían
    IP/navegador explícitos (login, logout, intento con usuario inexistente) no se tocaron. 2
    verificaciones nuevas en `pruebas/humo_http.sh` (aprobar solicitud queda con IP/navegador de
    curl; el recálculo diario de mora sigue sin IP). `mvn test` 776/776. Probado en pantalla por
    Joel: "Enviar solicitud a aprobación"/"Aprobar solicitud" de `SOL-2026-0006` con IP/Navegador
    ya completos; el registro previo al despliegue (`SOL-2026-0019`) sigue vacío (dato real, no
    se reescribe); "Recálculo diario de mora" sigue con Usuario "Sistema" e IP/Navegador vacíos.
    Ver AVANCE.md para el detalle completo.
12. **`CONSULTAR_AUDITORIA` fantasma** — ver punto 8 arriba (sin cambios respecto a la sesión
    anterior, re-confirmado como pendiente documentado).
13. **PDF de auditoría — 3 detalles de maquetación (Lote 2, junto con las demás correcciones
    finas pendientes de otros RF)**: la columna "Entidad" corta la palabra a la mitad (ej.
    "PARAMETRO_SIST" / "EMA" en dos líneas) — columna angosta (65pt) para valores largos como
    `PARAMETRO_SISTEMA`; sin logo (el resto de los informes tampoco lo tiene, no es específico
    de auditoría); pie de página "Página 1 de    1" con espacio de más antes del segundo número
    (`$V{PAGE_NUMBER}` en un campo aparte sin ajustar el ancho del texto fijo "Página X de").
    No implementado.

## 11. Guion para la defensa / guion de prueba en pantalla propuesto

**Ejecutado y aprobado por Joel el 13/10/2026** (ver §9 para el detalle completo):

1. **Gerente ve la auditoría, con Usuario y Entidad como listas.** ✅ Hecho (F1/F2 — el guion
   original decía "filtrar por `tipoEntidad=PRESTAMO`" a mano en la URL; con F1/F2 ya es un
   `<select>` en pantalla, probado con "Joel Jesús Acosta" + `PARAMETRO_SISTEMA`).
2. **Oficial, "Acceso restringido".** ✅ Hecho.
3. **Cajera, "Acceso restringido".** ✅ Hecho.
4. **Exportar PDF y CSV con las columnas nuevas.** ✅ Hecho (F3 — a diferencia del guion
   original, que esperaba que NO las trajeran; ahora sí, con los mismos filtros y las mismas 4
   filas que la pantalla).
5. **Detalle sin cortar, con salto de línea.** ✅ Hecho (F4, agregado al guion original).
6. **Los 11 `.jasper` restaurados no cambiaron nada.** ✅ Hecho (recibo/factura de
   `CC-2026-00028` iguales que antes).

**No probado en pantalla** (es una confirmación de código, no de pantalla): ningún `DELETE` en
el código, ver §6d.
