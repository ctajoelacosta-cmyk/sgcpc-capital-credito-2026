# RF08 — Resumen de la solicitud (ficha de aprobación)

**Estado: probado en pantalla completo, las tres vueltas — tag `rf08-probado` (segunda vuelta) y
confirmado de nuevo junto con `rf09-probado`/`rf10-probado` (tercera vuelta, 11/10/2026).** Las
decisiones de las tres vueltas (franja de mora/riesgo alto arriba de todo con la consulta más
reciente por persona, re-vinculación y detalle de auditoría al decidir, confirmación al
registrar D/E sin mora, caché del navegador centralizado, 10 textos en usted, y las 4 mejoras
menores a-d — hora en la tabla del buró, badge gris, botón con texto propio y foco en Cancelar,
aviso oculto si ya decidida) quedaron todas probadas por Joel en pantalla, incluida la
re-vinculación (la única parte sin cobertura de JUnit). El PDF del resumen sigue como trabajo
futuro (sección 12), no se implementó.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF08 (módulo Aprobación):

> El sistema debe mostrar el resumen de cada solicitud: datos del cliente (edad, zona,
> situación laboral, empleador y otras deudas), condiciones del préstamo, afectación del
> ingreso y consultas al buró, con un aviso si el buró indica mora.

RF08 depende de RF07 (la bandeja es la puerta de entrada a esta ficha) y convive con RF09
(aprobar) y RF10 (rechazar), que agregan los botones de decisión sobre el mismo resumen — ver
sección 5.

## 2. Cómo funciona

Todo lo de abajo se ve en `aprobaciones/ficha.jsp` (`GET /aprobaciones/{id}`), armado por
`ControladorAprobacionesServlet.mostrarFicha`.

### 2.1 Datos del cliente: edad, zona, situación laboral, empleador y otras deudas

Card "Cliente" (líneas 74-96), bloque agregado en Bloque 7/E10 (D-129, Obs. 26) "para que el
gerente decida sin entrar a la ficha del cliente":

- **Edad**: `edadCliente` (`ServicioSolicitudes.calcularEdad`, con la fecha de nacimiento y
  `Reloj.hoy()` — recalculada en cada carga, nunca una edad congelada de cuando se creó la
  solicitud).
- **Zona**: departamento y ciudad del cliente (`servicioClientes.buscarDepartamentoPorId` /
  `buscarCiudadPorId`).
- **Situación laboral**: `ServicioClientes.etiquetaSituacionLaboral(...)`.
- **Empleador**: nombre y antigüedad en años (si están cargados; "-" si no).
- **Otras deudas**: `cuotaOtrasDeudasMensual`, en guaraníes.

**Cumple** los 5 datos que pide el texto, en una sola línea/párrafo arriba de todo en la card
"Cliente" — visibles sin bajar en la pantalla (es la primera card, justo debajo de la franja de
estado/decisión).

### 2.2 Condiciones del préstamo

Card "Condiciones solicitadas y simulación" (líneas 142-197): monto solicitado (con el monto en
letras), plazo, destino del crédito, primera fecha de vencimiento estimada, cuota más alta con
IVA, TEA/TNA, CFT, total a pagar con IVA, y afectación al ingreso (ver 2.3). Cada dato
financiero tiene su ⓘ con el glosario de RF01 (agregado en RF06).

**Cumple** — están todas las condiciones del préstamo que simuló el oficial al crear la
solicitud (no las que el gerente podría cambiar al aprobar con otro monto/plazo; eso es lo que
el oficial propuso, que es lo que hay para revisar antes de decidir).

### 2.3 Afectación del ingreso

Fila propia dentro de la misma card de condiciones (líneas 186-194): porcentaje con 1 decimal
(`comoPorcentajeUnDecimal`, ajustado en RF06), el límite aplicado al lado, y la fila entera
resaltada en amarillo (`table-warning`) si supera el límite o en verde (`table-success`) si no
— con su ⓘ del glosario. **Cumple**, y además es visualmente clara (color), no solo el número.

### 2.4 Consultas al buró

Card "Resultado del buró (Informconf)" (líneas 199-240): tabla con Persona, Fecha, Puntaje,
Clasificación, ¿Mora?, Resumen y Evidencia (enlace "Ver" al documento adjunto, si tiene).

- `ControladorAprobacionesServlet.mostrarFicha` llama
  `servicioAprobacionCredito.listarConsultasDeLaSolicitud` →
  `ConsultaCrediticiaDAO.listarPorSolicitud(conexion, idSolicitud)`:
  ```sql
  SELECT ... FROM consultas_crediticias WHERE id_solicitud = ? ORDER BY fecha_hora_consulta DESC
  ```
  Trae todas las consultas vinculadas a esa solicitud por `id_solicitud` (hoy, siempre la del
  cliente — hasta el 18/10/2026 también podía traer la del codeudor, si tenía una vinculada a
  la misma solicitud; retirado junto con el codeudor, ver §10). **Cumple.**
- Las consultas se vinculan a la solicitud recién cuando el oficial la crea/envía
  (`ServicioSolicitudes`, `agregarFaltantesBuro`/`vincularSolicitud` →
  `ConsultaCrediticiaDAO.vincularSolicitud`, `UPDATE consultas_crediticias SET id_solicitud =
  ?`) — la consulta en sí se registra antes, en la ficha del cliente (paso ③, antes de que
  exista la solicitud).

### 2.5 Aviso si el buró indica mora

```java
boolean hayMora = consultas.stream().anyMatch(c -> Boolean.TRUE.equals(c.getIndicadorMora()));
```

Si `hayMora` es verdadero, dentro de la misma card del buró (línea 236-238):

```html
<div class="alert alert-danger">El buró indica mora. Revise antes de decidir.</div>
```

**Cómo se ve**: alerta roja, con texto claro, dentro de la card "Resultado del buró". También
refuerza el botón "Aprobar": si hay mora, la confirmación cambia de "¿Aprobar esta solicitud?"
a "El buró indica mora. ¿Aprobar igualmente?" (línea 246) — un segundo lugar donde el gerente
se entera, aunque ya esté decidido a aprobar.

**Dónde aparece / visible sin bajar**: **No cumple** la parte de "visible sin bajar en la
pantalla". La card del buró es la **tercera** de la ficha (Cliente → Condiciones del préstamo →
Resultado del buró) — en una pantalla de notebook normal, con la card de Condiciones ya
ocupando varias filas (8 filas de tabla + textos en letras), hay que desplazarse para llegar a
la card del buró y ver el aviso. Un gerente que decide mirando solo "lo que entra en la
pantalla" puede no verlo sin bajar.

**¿También avisa con clasificación D o E aunque no diga mora?** **No cumple.** `hayMora` se
calcula **solo** con `indicador_mora` (un booleano). `clasificacion_obtenida` ("A" a "E") es un
campo **totalmente independiente**, elegido en un `<select>` separado al registrar la consulta
(`clientes/ficha.jsp`, paso ③) — no hay ninguna regla que fuerce "si la clasificación es D o E,
marcar mora". Confirmado en el código de registro
(`clientes/ficha.jsp` líneas 1390-1406, dos controles sin relación entre sí: el `<select>` de
Clasificación y los botones "¿Tiene deudas en mora?") y en
`ServicioConsultaCrediticia.validarIndicadorMora` (solo exige que **no sea null**, no exige
ninguna coherencia con la clasificación). Es perfectamente posible hoy registrar una consulta
con clasificación "E" y "No" en mora — esa solicitud llegaría a la ficha del gerente **sin**
ninguna alerta roja, mostrando "E" en una columna de texto plano, sin resaltar.

Esto es consistente con cómo el anteproyecto y la especificación maestra tratan a Informconf en
general (RN-18: "el oficial lo registra y el gerente rechaza con ese motivo" — es un juicio
manual del gerente, no un bloqueo automático), pero el texto de RF08 pregunta explícitamente
por un aviso ante D/E, y hoy ese aviso no existe — solo existe para `indicador_mora`.

### 2.6 ¿Se puede imprimir o exportar el resumen en PDF?

**No cumple.** No se encontró ningún botón "Imprimir" ni ninguna ruta que genere un PDF de esta
ficha. Se buscó específicamente:

- `aprobaciones/ficha.jsp`: ningún enlace ni botón de impresión/exportación.
- `ServicioInformes`/`GeneradorInformesPdf`: el único PDF relacionado con aprobaciones es
  `informe_aprobaciones.jasper`, que es el **listado** de RF07 (`/reportes/aprobaciones`,
  varias solicitudes en una tabla, un período) — no el resumen de **una** solicitud para la
  carpeta física del cliente.
- Lo más parecido que existe en el sistema es `requisitos_prestamo.jrxml` ("Imprimir
  requisitos" en la ficha del cliente), pero es otro documento (los requisitos del crédito, no
  el resumen de evaluación con datos del cliente + condiciones + buró).

Si Joel quiere esto, es un PDF nuevo (mismo patrón que el resto: preguntar antes de diseñar
cualquier reporte, `REGLAS_DESARROLLO.md` §6) — se deja como propuesta en la sección 8, sin
tocar nada.

## 3. Auditoría

Igual que RF07: esta pantalla es de **lectura** (el resumen en sí). No genera eventos propios.
Los eventos que sustentan los datos que se muestran ya los registran otros RF:

- `ENVIAR_SOLICITUD_APROBACION` (RF06) — por qué la solicitud y sus consultas vinculadas llegan
  a esta ficha.
- El registro de la consulta en sí (`clientes/ficha.jsp`, paso ③) no quedó confirmado en este
  análisis si genera un evento de auditoría propio — fuera del alcance de RF08 (es un paso de
  RF04), no se investigó más a fondo acá.
- `APROBAR_SOLICITUD`/`RECHAZAR_SOLICITUD`/`CANCELAR_SOLICITUD` (RF09/RF10/RF06) — las acciones
  que se toman DESPUÉS de leer este resumen, no el resumen en sí.

## 4. Dónde está

- **Modelo**: `SolicitudCredito`, `Persona` (datos del cliente), `ConsultaCrediticia`
  (`idPersona`, `idSolicitud`, `puntajeObtenido`, `clasificacionObtenida`, `indicadorMora`,
  `resumenResultado`, `idDocumentoResultado`), `ResultadoCotizacion` (total a pagar exacto).
- **DAO**: `ConsultaCrediticiaDAO.listarPorSolicitud`, `vincularSolicitud`.
- **Servicio**: `ServicioAprobacionCredito.listarConsultasDeLaSolicitud`;
  `ServicioSolicitudes.calcularEdad`, `recalcularPlan`, `buscarCompromisoAbierto`;
  `ServicioClientes.buscarDepartamentoPorId`/`buscarCiudadPorId`/`etiquetaSituacionLaboral`;
  `ServicioConsultaCrediticia.validarIndicadorMora` (solo exige no-nulo, sin relación con la
  clasificación).
- **Controlador**: `ControladorAprobacionesServlet.mostrarFicha`.
- **Vista**: `aprobaciones/ficha.jsp`.
- **Permisos**: los mismos de RF07 (`Permiso.APROBAR_RECHAZAR_SOLICITUD`, gerente y
  administrador TOTAL; oficial y cajero NINGUNO) — no se repite el análisis acá, ver
  `RF07_bandeja_aprobacion.md` §2.3.

## 5. Dependencias

- **RF07**: la bandeja es la única puerta de entrada normal a esta ficha (botón "Revisar"/"Ver")
  — un cambio en qué solicitudes lista RF07 cambia qué se puede llegar a resumir acá.
- **RF04**: quién registra la consulta al buró y con qué datos (clasificación/mora
  independientes) — la causa del hallazgo de la sección 2.5 vive en RF04, no en RF08; RF08 solo
  **muestra** lo que RF04 dejó guardado.
- **RF06**: cuándo se vincula una consulta a una solicitud (`vincularSolicitud`, al enviar a
  aprobación) — antes de eso, la consulta existe pero no aparecería en esta ficha.
- **RF09** (aprobar) y **RF10** (rechazar): comparten la misma pantalla y los mismos datos de
  este resumen — el gerente decide mirando exactamente lo que describe este documento.
- Un cambio en RF08 (por ejemplo, agregar el aviso de clasificación D/E) no debería afectar a
  RF04/RF06/RF07/RF09/RF10 — es una pantalla de lectura sobre datos que esos RF ya escriben.

## 6. Análisis

### Análisis del 11/10/2026, primera vuelta (texto firmado contra el código)

Hecho a pedido de Joel, revisando el texto firmado de RF08 contra el código real
(`aprobaciones/ficha.jsp` y `ControladorAprobacionesServlet.mostrarFicha`), sin cambiar nada.
Resultado resumido en la tabla de la sección 7.

### Análisis del 11/10/2026, segunda vuelta (prueba en pantalla: hallazgo A, caché B, textos C)

Solo análisis, sin cambiar código (pedido explícito de Joel) — a la espera de su aprobación
antes de programar nada de lo que sigue.

#### A) Hallazgo — el gerente no ve una consulta con mora registrada después del envío

**Caso que lo disparó**: SOL-2026-0004 (Luis Alberto Cabrera Núñez) se envió a aprobación con
una consulta 410 · E · sin mora. Ya `PENDIENTE_APROBACION`, se registró una consulta nueva en
la ficha del cliente: 410 · E · **con** mora. En `/aprobaciones/{id}` (con Ctrl+Shift+R, para
descartar caché) la franja sigue naranja y la tabla del buró sigue mostrando la consulta vieja,
con ¿Mora? = No.

**Cómo se vincula hoy una consulta con una solicitud** (la pregunta exacta de Joel):

1. `ServicioConsultaCrediticia.obtenerParaVincularSolicitud(conexion, idPersona, etiquetaPersona)`
   (`src/main/java/.../servicios/ServicioConsultaCrediticia.java:180-195`) — llama
   `ConsultaCrediticiaDAO.listarPorPersona` (línea 73-79:
   `SELECT ... WHERE id_persona = ? ORDER BY fecha_hora_consulta DESC`, es decir, la más
   reciente primero) y devuelve la **primera** de esa lista que tenga evidencia
   (`id_documento_resultado`) y sea posterior al consentimiento vigente. Es la consulta más
   reciente y válida **en el momento en que se llama** — no se vuelve a evaluar después.
2. Quien la llama es `ServicioSolicitudes.agregarFaltantesBuro`
   (`servicios/ServicioSolicitudes.java:710-724`), a su vez llamado desde
   `ServicioSolicitudes.enviarAAprobacion` (líneas 603-606) **una sola vez, en el momento del
   envío**.
3. Ahí mismo, después de marcar la solicitud como enviada,
   `ServicioSolicitudes.enviarAAprobacion` (líneas 626-632) llama
   `ServicioConsultaCrediticia.vincularASolicitud(conexion, idConsulta, idSolicitud)` para el
   cliente — cada llamada ejecuta
   `ConsultaCrediticiaDAO.vincularASolicitud` (`dao/ConsultaCrediticiaDAO.java:62-67`):
   `UPDATE consultas_crediticias SET id_solicitud = ? WHERE id_consulta_crediticia = ?`. Es un
   `UPDATE` de una sola fila, una sola vez — la fila que se vincula queda fija para siempre
   (nada vuelve a tocar su `id_solicitud` después).
4. La ficha del gerente, `ServicioAprobacionCredito.listarConsultasDeLaSolicitud` →
   `ConsultaCrediticiaDAO.listarPorSolicitud` (`WHERE id_solicitud = ?`), **solo ve esa fila
   fija** — una consulta nueva, registrada después, no tiene `id_solicitud` puesto (sigue
   `NULL` hasta que alguien la vincule a otra cosa, lo que hoy no pasa nunca para una solicitud
   ya enviada) y por lo tanto es invisible para esta consulta, para `construirAvisoBuro` (que
   recibe exactamente esta misma lista) y para cualquier otro lugar que lea por `id_solicitud`.

**Es un snapshot de un solo momento (el envío), no una foto en vivo del buró** — exactamente el
mismo tipo de problema que ya se corrigió en RF06/RF07 para el checklist y para la validación
en vivo, pero al revés: ahí el problema era mostrar algo DEMASIADO en vivo sobre una decisión ya
tomada; acá el problema es mostrar algo DEMASIADO congelado sobre una decisión todavía
pendiente.

**Evaluación de la propuesta de Joel** — en general de acuerdo, con una pregunta abierta:

- *"La ficha del gerente y `construirAvisoBuro` usan la consulta MÁS RECIENTE de cada persona
  (cliente y garantes, si los hay)."* De acuerdo. (Nota 18/10/2026: en el momento de esta
  propuesta el sistema admitía un solo codeudor por solicitud — retirado junto con el codeudor,
  ver §10; hoy "cada persona" es solo el cliente.) Se puede reutilizar
  `ConsultaCrediticiaDAO.listarPorPersona` (ya existe, ya ordena DESC) y tomar el primer
  elemento de la lista — sin necesidad de una consulta SQL nueva.
- *"Si esa consulta es posterior al envío, mostrarlo en la tabla (ej. 'registrada después del
  envío') y que la franja lo diga."* De acuerdo — comparar
  `consulta.getFechaHoraConsulta()` contra `solicitud.getFechaEnvioAprobacion()` (dato que la
  ficha ya tiene cargado). Si es posterior, una nota en la fila de la tabla y, en la franja,
  algo como "(consulta registrada después del envío, el `{fecha}` enviado no la tenía en
  cuenta)" — a redactar con Joel cuando se programe.
- *"Conservar la consulta del envío para la trazabilidad (auditoría)."* Ya está conservada: el
  `UPDATE` de `vincularASolicitud` no se tocaría, la fila vinculada en el envío sigue existiendo
  con su `id_solicitud` tal cual. El cambio sería solo en qué se **muestra** (la más reciente
  por persona, no la vinculada), no en qué se **guarda**.
- *"El refuerzo al tocar Aprobar tiene que usar la misma consulta que la franja."* De acuerdo y
  ya es así estructuralmente: `construirAvisoBuro` ya alimenta tanto la franja de arriba como el
  texto de `data-confirmar` del botón "Aprobar" (`aprobaciones/ficha.jsp`, ver RF08 sección 9)
  — si `construirAvisoBuro` pasa a usar la consulta más reciente por persona en vez de
  `listarPorSolicitud`, el refuerzo de "Aprobar" queda automáticamente sincronizado sin tocarlo
  aparte.
- **Pregunta abierta, no decidida acá**: si el gerente aprueba viendo la consulta nueva (con
  mora), ¿conviene re-vincular esa consulta a la solicitud en ese momento (para que
  `listarPorSolicitud`/la auditoría de "qué consulta sustentó esta decisión" apunte a la que
  realmente se vio), o se deja la vinculación del envío tal cual y la trazabilidad de "qué se
  vio al decidir" queda solo en que la pantalla mostró la más reciente en ese momento (sin
  guardar cuál era)? Esto no estaba en la propuesta de Joel y conviene que él lo resuelva antes
  de programar, porque toca a `ServicioAprobacionCredito.aprobar`/`rechazar` (RF09/RF10), no
  solo a la pantalla de RF08.

**Dónde más se muestra el buró de una solicitud** (lo que pidió revisar Joel):

- `aprobaciones/lista.jsp` (bandeja, RF07): **no aplica** — no muestra ninguna columna de buró
  ni de mora, solo Código/Enviada/Cliente/Monto/Plazo/Afectación/Decisión. Verificado leyendo
  el archivo completo.
- `/reportes/aprobaciones` (`ServicioReportes.listarInformeAprobaciones`,
  `FilaInformeAprobacion`): **no aplica** — ese informe no tiene ningún campo de
  clasificación/mora, solo monto/decisión/tiempos/motivo.
- `/reportes/consultas-buro` (`ConsultaCrediticiaDAO.listarParaInforme`,
  `src/main/java/.../dao/ConsultaCrediticiaDAO.java:125-137`): **no aplica** — es un listado de
  auditoría de TODAS las consultas en un rango de fechas (con un `LEFT JOIN` al código de
  solicitud solo para mostrarlo si existe), muestra cada consulta tal cual es, no "la consulta
  de tal solicitud" ni tiene ningún concepto de aviso de mora — no hereda este problema.
- `clientes/ficha.jsp` (historial de consultas de la persona, paso ③): **no aplica** — ya
  muestra todas las consultas de la persona (`listarPorPersona`, sin filtrar por solicitud), ya
  incluye la más nueva.
- **Conclusión**: el problema está **solo** en `aprobaciones/ficha.jsp` y en
  `ServicioAprobacionCredito.construirAvisoBuro`/`listarConsultasDeLaSolicitud` — el único lugar
  que lee el buró "de una solicitud" (por `id_solicitud`) en vez de "de una persona".

#### B) Caché del navegador

**Lo que ya existe** (D-146, `plantilla/pie.jsp` líneas 3-9): un cache-busting `?v=` con
`VERSION_ASSETS = String.valueOf(System.currentTimeMillis())`, una declaración de JSP que se
inicializa una sola vez por despliegue — ya aplicado a 8 de los `.js` propios que carga
`pie.jsp` (líneas 31-38: `campo-guaranies.js`, `campo-cedula.js`, `campo-contrasena.js`,
`aviso-sesion.js`, `listado-vivo.js`, `buscar-en-tabla.js`, `fecha-primer-vencimiento.js`,
`cobranza-ficha.js`). El comentario de esa misma declaración dice explícitamente que esto es
"para que el navegador baje la versión nueva después de un redeploy sin necesitar
Ctrl+Shift+R" — exactamente el síntoma de esta vuelta de pruebas.

**Lo que falta** (2 archivos `.css`/`.js` propios sin `?v=`, encontrados al buscar todas las
referencias a `recursos/css/` y `recursos/js/` en todo `src/main/webapp`):

1. `plantilla/cabecera.jsp:817` — `<link rel="stylesheet"
   href="${pageContext.request.contextPath}/recursos/css/sgcpc-tema.css">`. **Este es,
   probablemente, la causa real de que la franja naranja no se viera**: la clase nueva
   `.alert-riesgo-alto` vive en este archivo (agregada en la decisión 2 de RF08, sección 9) —
   si el navegador tenía la versión vieja de `sgcpc-tema.css` cacheada, el HTML nuevo
   (`class="alert alert-riesgo-alto"`) se dibujaba sin ese estilo, lo que puede explicar por
   qué "no se veía" antes del Ctrl+Shift+R.
2. `reportes/indice.jsp:6` y `:257` — `recursos/css/indice-reportes.css` y
   `recursos/js/indice-reportes.js`, los dos sin `?v=`.

**Lo que NO explica el caché de `.css`/`.js`**: la confirmación de D/E sin mora
(`clientes/ficha.jsp`) es JavaScript **inline**, dentro del HTML de esa misma página, no un
`.js` separado — un `?v=` en un archivo externo no tiene nada que ver con eso. Y el HTML de las
páginas autenticadas ya sale con cabeceras `Cache-Control: no-store, no-cache, must-revalidate`
+ `Pragma: no-cache` + `Expires: 0` en **todas** las rutas no públicas
(`FiltroAutenticacion.java:83-85` — ya existente, de un hallazgo de seguridad anterior sobre el
botón "atrás"; `esRutaPublica` solo exceptúa `/recursos/`, `/acceso`, `/index.jsp` y `/`), así
que en principio el HTML de `aprobaciones/{id}` y `clientes/{id}` no debería quedar cacheado en
absoluto. La explicación más probable para que la confirmación tampoco se viera antes del
Ctrl+Shift+R no es un caché de **contenido** sino más bien el propio ciclo de recompilación de
Tomcat durante el Clean and Build (la JSP tarda un instante en recompilarse la primera vez que
se la pide después de desplegar) — no es algo que un `?v=` en un asset resuelva, y no se
investigó más a fondo porque no hay manera de reproducirlo de forma controlada sin tocar el
servidor en vivo durante un despliegue.

**Propuesta** (2 archivos a tocar, siguiendo el patrón D-146 ya aprobado, no uno nuevo):

- Agregar `?v=<%= VERSION_ASSETS %>` al `<link>` de `sgcpc-tema.css` en `cabecera.jsp`.
- Agregar `?v=...` a `indice-reportes.css`/`indice-reportes.js` en `reportes/indice.jsp`.
- Problema de diseño menor: `VERSION_ASSETS` hoy es una declaración `<%! %>` **privada de
  `pie.jsp`** — `cabecera.jsp` (que se incluye ANTES de `pie.jsp` en cada página) no tiene
  acceso a ese valor. Dos caminos: (a) declarar una constante igual, por separado, en
  `cabecera.jsp` (dos relojes de build distintos, cada uno válido para sus propios archivos,
  pero dos valores diferentes si alguien los compara) — 2 archivos tocados, sin Java nuevo; o
  (b) mover `VERSION_ASSETS` a una clase chica de infraestructura (ej.
  `py.com.capitalcredito.sgcpc.infraestructura.VersionDespliegue`, un campo estático
  equivalente) que `cabecera.jsp`, `pie.jsp` y `reportes/indice.jsp` importen — un solo valor
  compartido por todo el despliegue, más prolijo. **Total: 3 archivos `.jsp` + 1 clase Java
  nueva si se elige (b); 3 archivos `.jsp` sin Java nuevo si se elige (a).** Ninguna de las dos
  opciones toca el resto de los JSP del sistema (que no cargan ningún `.css`/`.js` propio
  directamente — todo lo demás ya pasa por `cabecera.jsp`/`pie.jsp`).

#### C) Textos (REGLAS_DESARROLLO.md §12)

**C.1 — Cartel de éxito al registrar una consulta** (`ControladorClientesServlet.java:231`):

```java
MensajesFlash.guardarExito(request.getSession(), "Resultado de la consulta registrado.");
```

Propuesta: `"Consulta registrada. " + puntaje/clasificación/mora ya resueltos en
procesarConsulta (líneas 538-547), ej. "410 · E · con mora."` — hoy esos tres valores se leen
y se usan dentro de `procesarConsulta` (método `private`, no los devuelve); para armar el
detalle hay que o devolverlos desde ahí, o releer los mismos tres `request.getParameter(...)`
en `procesarConsultaBuro` después de llamar `procesarConsulta`. A definir con Joel el separador
exacto (" · " como en su ejemplo) y qué mostrar cuando falta el puntaje o la clasificación
(¿omitir ese segmento, o "sin puntaje"/"sin clasificación"?).

**C.2 — Aviso amarillo al marcar mora "Sí"** (`clientes/ficha.jsp:1405`):

```html
<div class="alert alert-warning mt-2 mb-0 d-none" id="avisoMora">Tener en cuenta en la aprobación.</div>
```

Propuesta (la de Joel, sin cambios): **"El gerente verá este atraso al revisar la
solicitud."** — más concreto que "tener en cuenta", dice explícitamente qué pasa con ese dato.

**C.3 — Modal "Autorización N.º … generada"** (`ControladorClientesServlet.java:815-816`):

```java
MensajesFlash.guardarExito(request.getSession(), "Autorización N.º " + numero + " generada. Siguiente "
        + "paso: imprimila, que el cliente la firme y cargala escaneada.", imprimibles);
```

Propuesta: `"Autorización N.º " + numero + " generada. Siguiente paso: imprímala, que el
cliente la firme, y cárguela escaneada."` (la estructura título/detalle ya es correcta, solo
cambian los tres verbos voseados).

**C.4 — Barrido completo de voseo/tuteo y de botones Aceptar/Entendido**:

Botones de SweetAlert2 revisados (`confirmButtonText` en todos los `.jsp` y `.js` propios):
**sin hallazgos** — los carteles de éxito ya usan "Aceptar", los de error/aviso informativo ya
usan "Entendido", y los que piden una decisión ya usan botones propios de esa decisión ("Sí"/
"Cancelar", "Seguir conectado", "Ir a su ficha", "Sí, no hay mora") — ninguno usa "Aceptar"/
"Entendido" para pedir una decisión, ninguna decisión usa un botón genérico. No se encontró
ningún caso para corregir en esta regla.

Voseo/tuteo encontrado (barrido con una lista de verbos e imperativos voseados —
`cargá/cargala/imprimí/imprimila/revisá/tocá/ingresá/elegí/confirmá/podés/tenés/querés/sabés/
estás/sos/vos/...` — sobre todos los `.jsp` de `src/main/webapp/WEB-INF/vistas`, todos los
`.js` propios de `recursos/js/` y los textos armados en `src/main/java`; no es necesariamente
exhaustivo de cualquier redacción posible, pero cubre los patrones más comunes y los que Joel
nombró como ejemplo):

| Archivo | Línea | Texto actual | Texto propuesto |
|---|---|---|---|
| `plantilla/cabecera.jsp` | 840 | "Estás usando la base de pruebas, no la oficial" | "Está usando la base de pruebas, no la oficial" |
| `clientes/ficha.jsp` | 1017 | "...Genera el formulario de autorización con los datos del cliente, la fecha y un número único. Imprimilo y hacelo firmar en el momento. Si se traba la impresora, 'Reimprimir' saca el mismo documento sin cambiar el número." (texto del glosario ⓘ) | "...Imprímalo y hágalo firmar en el momento. Si se traba la impresora, 'Reimprimir' saca el mismo documento sin cambiar el número." |
| `clientes/ficha.jsp` | 1052 | "Escaneá la hoja firmada por el cliente y cargala acá. Debe ser el mismo día en que se generó..." (texto del glosario ⓘ) | "Escanee la hoja firmada por el cliente y cárguela acá. Debe ser el mismo día en que se generó..." |
| `desembolso/ficha.jsp` | 134 | "Pedí al gerente que entregue efectivo." | "Pida al gerente que entregue efectivo." |
| `controladores/ControladorClientesServlet.java` | 231 | "Resultado de la consulta registrado." | Ver C.1 arriba (cartel + detalle nuevo) |
| `controladores/ControladorClientesServlet.java` | 815-816 | "...Siguiente paso: imprimila, que el cliente la firme y cargala escaneada." | Ver C.3 arriba |
| `servicios/ServicioDocumentos.java` | 60-61 | "No se pudo cargar el archivo. El archivo no es un PDF, JPG o PNG real. Volvé a escanear el documento y guardalo como PDF." | "...Vuelva a escanear el documento y guárdelo como PDF." |
| `servicios/ServicioDocumentos.java` | 100-101 | "El archivo supera N MB. Escanealo con menor resolución o en blanco y negro para que pese menos." | "...Escanéelo con menor resolución o en blanco y negro para que pese menos." |
| `servicios/ServicioDocumentos.java` | 106-107 | "El archivo no coincide con su tipo. Parece un archivo con la extensión cambiada. Guardalo de nuevo como PDF, JPG o PNG y volvé a cargarlo." | "...Guárdelo de nuevo como PDF, JPG o PNG y vuelva a cargarlo." |
| `filtros/FiltroCsrf.java` | 34 | "El formulario venció o no es válido. Volvé a intentarlo." | "El formulario venció o no es válido. Vuelva a intentarlo." |

No se encontró ningún "tu"/"tus" (posesivo informal) nuevo en pantalla — el único caso ya
corregido (RF06, `menu.jsp`, "su perfil") sigue así; el barrido de `\btu\b`/`\btus\b` en
`.jsp` y en `src/main/java` no encontró otro caso en texto visible (solo comentarios de código,
que no son texto de pantalla).

## 7. Tabla de cumplimiento

| Parte del texto / pregunta de Joel | Cumple | Dónde / detalle |
|---|---|---|
| Datos del cliente: edad | **Sí** | Card "Cliente", `edadCliente` (`ServicioSolicitudes.calcularEdad`, recalculada con `Reloj.hoy()`) |
| Datos del cliente: zona | **Sí** | Departamento/ciudad del cliente |
| Datos del cliente: situación laboral | **Sí** | `ServicioClientes.etiquetaSituacionLaboral` |
| Datos del cliente: empleador | **Sí** | Nombre + antigüedad en años |
| Datos del cliente: otras deudas | **Sí** | `cuotaOtrasDeudasMensual` |
| Condiciones del préstamo | **Sí** | Monto, plazo, destino, 1er vencimiento, cuota, TEA/TNA, CFT, total a pagar — con glosario ⓘ |
| Afectación del ingreso | **Sí** | Con 1 decimal, límite aplicado, fila resaltada en color |
| Consultas al buró (cliente) | **Sí** | `ConsultaCrediticiaDAO.listarPorSolicitud`, por `id_solicitud` |
| Aviso si el buró indica mora: existe y es claro | **Sí** | Alerta roja "El buró indica mora. Revise antes de decidir." + refuerzo en la confirmación de "Aprobar" |
| Aviso: visible sin bajar en la pantalla | **No** | Está en la 3.ª card (Cliente → Condiciones → Buró), después de una tabla de condiciones larga |
| Aviso: también con clasificación D/E sin indicador de mora | **No** | `hayMora` solo mira `indicadorMora`; `clasificacionObtenida` es un campo independiente, sin ninguna regla que los vincule |
| ¿Se puede imprimir/exportar el resumen en PDF? | **No** | No existe ningún botón ni ruta de PDF para esta ficha; el único PDF de "aprobaciones" es el listado de RF07, no el resumen de una solicitud |

**Tabla de arriba: foto del 11/10/2026, antes de las decisiones.** Después de implementar las
decisiones de la sección 9 (a confirmar en pantalla): el aviso de mora pasa a verse arriba de
todo, sin bajar, y se agrega el mismo tipo de aviso (naranja) para la clasificación D/E sin
mora. El PDF del resumen queda tal cual (**No** — decisión explícita de Joel de dejarlo para
trabajo futuro, sección 12).

## 8. Propuestas (para que Joel decida)

1. **Aviso también visible arriba, sin bajar**: duplicar (o mover) el aviso de mora a la franja
   superior de la ficha (junto al badge de estado, antes de la card "Cliente"), para que un
   gerente que decide rápido lo vea sin necesidad de llegar a la card del buró. La tabla
   detallada seguiría abajo, donde está hoy.
2. **Aviso también con clasificación D o E**: calcular `hayMora` (o un nuevo aviso aparte, para
   no mezclar los dos motivos) también cuando alguna consulta tenga `clasificacionObtenida` en
   ("D", "E"), aunque `indicadorMora` sea `false` o esté sin marcar. A definir con Joel si el
   texto del aviso debe distinguir "indica mora" de "clasificación de riesgo D/E" (son avisos de
   naturaleza distinta) o si alcanza con una sola alerta roja que cubra ambos casos.
3. **PDF del resumen para la carpeta física**: un informe nuevo (mismo patrón que
   `informe_aprobaciones.jasper`, pero de una sola solicitud) con los mismos datos de esta
   ficha — cliente (edad/zona/situación laboral/empleador/deudas), condiciones del préstamo,
   afectación, consultas al buró y la decisión final si ya está tomada.
   Como es un reporte nuevo, se diseña recién cuando Joel lo confirme explícitamente
   (`REGLAS_DESARROLLO.md` §6) — no se arma ningún `.jrxml` sin ese visto bueno.

## 9. Decisiones

Decisiones del 11/10/2026 (Joel), sobre las propuestas de la sección 8:

1. **Franja de mora arriba de todo, antes de la tarjeta Cliente**: roja, "El buró indica mora:
   (persona) — Informconf (fecha)." Se mantiene también la de la tarjeta del buró (sin cambios
   de texto) y el refuerzo en la confirmación de "Aprobar" (sin cambios de texto para este
   caso).
2. **Franja de riesgo alto (clasificación D/E), también arriba**: naranja, "Riesgo alto según
   Informconf: clasificación (letra) — (persona)", aunque el indicador de mora diga "No". Si
   hay mora y D/E a la vez, se muestra **solo la roja**, con la clasificación incluida en su
   línea (ver la regla exacta más abajo). También agrega su propio refuerzo en "Aprobar"
   ("Informconf indica clasificación de riesgo alto (D/E). ¿Aprobar igualmente?"), usado solo
   cuando no hay mora (la de mora tiene prioridad).
3. **Confirmación al registrar D/E con mora "No"** (`clientes/ficha.jsp`, paso ③): al tocar el
   botón "No" con clasificación D o E ya elegida, un SweetAlert2 de confirmación ("La
   clasificación (letra) suele indicar atrasos. ¿Confirma que Informconf no informa mora?") con
   "Sí, no hay mora" / "Cancelar". Si cancela, no se marca nada (el oficial puede elegir "Sí" o
   reconsiderar la clasificación). **No bloquea** — es una confirmación del navegador, el
   servidor sigue aceptando cualquier combinación (`ServicioConsultaCrediticia.validarIndicadorMora`
   no cambió, solo exige que no sea nulo).
4. **PDF del resumen**: **no se implementa** — queda anotado como trabajo futuro (sección 12).

### Implementación

- **`AvisoBuroSolicitud`** (DTO nuevo): `hayMora`, `hayRiesgoAlto`, `lineas` (ya formateadas).
- **`ServicioAprobacionCredito.construirAvisoBuro(consultas, cliente)`** (pura; hasta el
  18/10/2026 también recibía `codeudor` — retirado junto con el codeudor, ver §10): por
  cada consulta, si `indicadorMora` es verdadero, agrega una línea de mora (con "(clasificación
  X)" agregado si esa misma consulta también es D/E); si no tiene mora pero es D o E, agrega una
  línea de riesgo alto. `hayMora` = hay alguna línea de mora; `hayRiesgoAlto` = hay alguna línea
  de riesgo alto **sin** mora (un dato real, independiente de qué franja se pinte). Las líneas
  de mora y las de riesgo-alto-sin-mora se juntan en una sola lista — nada se pierde aunque
  después la JSP solo pinte un color.
- **`ControladorAprobacionesServlet.mostrarFicha`**: reemplaza el viejo `hayMora` (un booleano
  simple, `consultas.stream().anyMatch(...)`) por `avisoBuro` (el objeto completo).
- **`aprobaciones/ficha.jsp`**: franja nueva arriba (`avisoBuroRevision.isHayMora() ?
  "alert-danger" : "alert-riesgo-alto"`, mostrada si `hayMora` o `hayRiesgoAlto`) — como
  `hayMora` se mira primero, si las dos son verdaderas se pinta roja (nunca las dos franjas
  juntas). El aviso de la tarjeta del buró y el refuerzo de "Aprobar" ahora leen de
  `avisoBuroRevision` en vez del viejo booleano, mismo texto para mora, texto nuevo para riesgo
  alto.
- **`sgcpc-tema.css`**: clase nueva `.alert-riesgo-alto` (naranja, `#FFEADA`/`#FF8C00`/`#99550A`)
  — distinta de `.alert-warning` (ámbar, ya usada en el sistema para avisos genéricos), para que
  el gerente distinga de un vistazo mora (roja) de riesgo alto (naranja) de un aviso cualquiera
  (ámbar).
- **`clientes/ficha.jsp`**: el `<select>` de clasificación ahora tiene `id="selectClasificacionBuro"`;
  el botón "No" de mora, antes de llamar `elegirMora(false)`, revisa esa clasificación y si es D
  o E muestra el SweetAlert2 de confirmación del punto 3 — solo entonces llama `elegirMora(false)`.
- **Pruebas agregadas**: `ServicioAprobacionCreditoTest` —
  `testConstruirAvisoBuroSinMoraNiRiesgoAlto`, `testConstruirAvisoBuroSoloMora`,
  `testConstruirAvisoBuroSoloRiesgoAlto`, `testConstruirAvisoBuroMoraYRiesgoAltoJuntos` (misma
  consulta con las dos cosas). (Hasta el 18/10/2026 había además
  `testConstruirAvisoBuroClienteConMoraYCodeudorConRiesgoAlto`, de dos personas distintas —
  eliminada junto con el codeudor, ver §10.)

### Decisiones del 11/10/2026, segunda vuelta (A, B y C — aprobadas por Joel, implementadas)

**A) Consulta posterior al envío invisible para el gerente**

1. `aprobaciones/ficha.jsp` y `construirAvisoBuro` usan, del cliente, la
   consulta MÁS RECIENTE con evidencia (mismo criterio que `obtenerParaVincularSolicitud`; hasta
   el 18/10/2026 también por el codeudor, si había uno — retirado junto con el codeudor, ver
   §10), no
   la vinculada al enviar. Nuevo método puro en `ServicioConsultaCrediticia`:
   `obtenerMasRecienteConEvidencia` (y un helper privado `buscarMasRecienteConEvidencia`
   compartido con `obtenerParaVincularSolicitud`/`tieneConsultaConEvidenciaRegistrada`, para no
   triplicar el mismo bucle).
2. La tabla "Resultado del buró" muestra la vinculada original MÁS, si hay una más reciente por
   persona que todavía no está ahí, también esa, con la leyenda "Registrada después del envío".
   Nuevo DTO `FilaBuroSolicitud` (ya formateado entero, no confundir con `FilaConsultaBuro` del
   informe `/reportes/consultas-buro`, C-4.4 — son pantallas distintas) y
   `ServicioAprobacionCredito.construirFilasConsultaBuro`/`combinarFilasConsultaBuro` (esta
   última, pura, separada para poder probarla sin conexión).
3. El refuerzo de "Aprobar" ya usaba `avisoBuro`, que ahora se alimenta de la lista de "más
   recientes" en vez de la vinculada — queda sincronizado con la franja sin tocarlo aparte.
4. Al Aprobar o Rechazar (`ServicioAprobacionCredito.aprobar`/`rechazar`), en la misma
   transacción, `revincularConsultasMasRecientes` vincula también la consulta más reciente de
   cada persona si es distinta de la ya vinculada (la del envío sigue vinculada, nada se
   desvincula) — así `tieneMoraVinculada` (que decide el texto "(con indicador de mora del
   buró)" de la auditoría) ve el dato actualizado. El detalle de `APROBAR_SOLICITUD`/
   `RECHAZAR_SOLICITUD` ahora agrega, con `formatearConsultasVistasParaAuditoria`: "Consulta(s)
   vista(s) al decidir: [id, fecha, clasificación, mora]" por cada persona.
5. Pruebas: `testCombinarFilasConsultaBuroConsultaNuevaConMora` (el caso que rompía: vinculada
   sin mora + nueva con mora → 2 filas, la nueva marcada y con mora; `construirAvisoBuro` sobre
   la más reciente da roja) y `testCombinarFilasConsultaBuroSinConsultaNueva` (vinculada y más
   reciente son la misma → 1 fila, sin marca, igual que antes). **La re-vinculación en sí
   (punto 4) no se pudo probar con JUnit** — `revincularConsultasMasRecientes` necesita una
   `Connection` real (llama `ServicioClientes.buscarPorId` y
   `ConsultaCrediticiaDAO.vincularASolicitud`), y este proyecto no usa mocks ni tiene pruebas de
   DAO (`AVANCE.md`/los planes de etapa ya documentan "Pruebas JUnit (sin base)" como la única
   modalidad) — queda para la prueba en pantalla: aprobar o rechazar la solicitud con la
   consulta nueva y confirmar en la auditoría que el detalle trae la consulta vista.

**Nota para RF09/RF10** (pedido de Joel: "anotarlo como cambio hecho desde RF08"): la
re-vinculación al decidir y el nuevo detalle de auditoría viven en
`ServicioAprobacionCredito.aprobar`/`rechazar` — RF09 y RF10 todavía no tienen su propio
documento (`docs/funcionamiento/LEEME.md` los marca "Implementado, sin recorrido contra el
anteproyecto"); cuando se abra ese documento, referenciar este cambio en su Historial y
Decisiones en vez de repetir el análisis.

**B) Caché del navegador**: `VERSION_ASSETS` (antes solo en `pie.jsp`) se centraliza en
`py.com.capitalcredito.sgcpc.infraestructura.VersionDespliegue` (un campo estático,
`System.currentTimeMillis()` al cargar la clase, igual que antes pero en un solo lugar). Se
agrega `?v=VersionDespliegue.VALOR` a `sgcpc-tema.css` (`cabecera.jsp`) e
`indice-reportes.css`/`.js` (`reportes/indice.jsp`) — los dos que faltaban. `pie.jsp` sigue
exactamente igual por fuera (sus 8 `.js` ya tenían `?v=`), solo cambia de dónde saca el valor.

**C) Textos**:
1. Cartel de éxito al registrar una consulta: "Resultado de la consulta registrado." →
   "Consulta registrada. 410 · E · con mora" (mismo formato y mismas leyendas de reserva "sin
   puntaje"/"sin clasificación" que ya usaba la tarjeta de la ficha,
   `clientes/ficha.jsp` línea ~1363). Nuevo método privado
   `ControladorClientesServlet.resumenConsultaParaCartel`, releyendo los mismos tres parámetros
   que `procesarConsulta`.
2. Aviso al marcar mora "Sí": "Tener en cuenta en la aprobación." → "El gerente verá este
   atraso al revisar la solicitud." (texto de Joel, sin cambios).
3. Los 10 casos de voseo de la tabla de la sección 6, corregidos tal cual — detalle completo
   (archivo, línea, antes/después) en esa misma tabla.

### Decisiones del 11/10/2026, tercera vuelta (mejoras a-d, aprobadas por Joel junto con RF09/RF10)

Las 4 mejoras menores de la prueba de pantalla (antes listadas en Pendientes) — todas
implementadas:

a) **Hora en la tabla del buró**: `ServicioAprobacionCredito.combinarFilasConsultaBuro` pasó de
   `FormateadorFechas.comoDdMmAaaa` a `comoDdMmAaaaHhMm` para la columna Fecha —
   `FilaBuroSolicitud.getFechaFormateada()` ahora trae hora, así dos consultas del mismo día se
   distinguen.
b) **Color de "Registrada después del envío"**: de `badge-info` a `badge-secondary` (gris) —
   Joel ofreció rojo o gris; se eligió gris para no competir con el rojo de la fila cuando la
   consulta nueva además tiene mora (`table-danger`).
c) **Texto del botón y foco con mora**: mecanismo genérico nuevo en `plantilla/pie.jsp` —
   `data-confirmar-boton="texto"` (si el formulario no lo trae, sigue siendo `'Sí'`, sin tocar
   ningún otro `data-confirmar` del sistema). `aprobaciones/ficha.jsp`: "Sí, aprobar" en el
   formulario de Aprobar (siempre) + `data-confirmar-foco="cancelar"` solo con mora; "Sí,
   rechazar" en el de "Confirmar rechazo". Detalle completo en `RF09_aprobar.md` §9 (es el mismo
   cambio, documentado ahí porque toca el botón de RF09/RF10, no solo el resumen de RF08).
d) **No mostrar "Revise antes de decidir" si ya está decidida**: el aviso dentro de la card del
   buró ahora exige también `pendiente`, no solo `avisoBuro.isHayMora()`.

## 10. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 11/10/2026 | Probadas en pantalla las 4 mejoras menores a-d, junto con RF09/RF10 | *(pendiente)* | `rf09-probado` / `rf10-probado` |
| 11/10/2026 | Implementadas las 4 mejoras menores a-d (hora en la tabla del buró, badge gris, botón con texto propio y foco en Cancelar con mora, aviso de mora oculto si ya decidida) — mismo cambio que RF09/RF10 §9 | *(pendiente)* | — |
| 11/10/2026 | Probado en pantalla completo: franja roja/naranja con la consulta más reciente por persona, re-vinculación y detalle de auditoría al decidir, confirmación D/E sin mora, caché resuelto, 10 textos en usted | *(pendiente)* | `rf08-probado` |
| 11/10/2026 | Implementadas las decisiones de la segunda vuelta (A: consulta más reciente por persona + re-vinculación al decidir + detalle de auditoría; B: VersionDespliegue centralizado; C: 2 textos + 10 voseos corregidos), 7 pruebas nuevas, `mvn test` 637 en verde | *(pendiente)* | — |
| 11/10/2026 | Implementadas las decisiones de la sección 9 (franja de mora/riesgo alto arriba de todo, confirmación al registrar D/E sin mora), 5 pruebas nuevas | *(pendiente)* | — |
| 11/10/2026 | Análisis completo contra el texto firmado, sin cambios de código | `9d86937` | — |

## 11. Pruebas realizadas en pantalla

**11/10/2026 (Joel), primera vuelta, después del Clean and Build, con Ctrl+Shift+R:**
- Franja naranja en la ficha del gerente para SOL-2026-0004 (Luis, 410 · E · sin mora) — OK.
- Confirmación al registrar una consulta con clasificación E y mora "No" ("Sí, no hay mora" /
  "Cancelar"); al cancelar no se guarda y el formulario conserva los datos — OK.
- Antes del Ctrl+Shift+R no se veía ninguna de las dos cosas (ver hallazgo B, sección 6).
- **Hallazgo A**: con SOL-2026-0004 ya `PENDIENTE_APROBACION`, una consulta nueva (410 · E ·
  con mora) registrada después del envío no aparece en `/aprobaciones/{id}` — la franja sigue
  naranja y la tabla sigue mostrando la consulta vieja sin mora. Analizado en la sección 6,
  corregido en la segunda vuelta de decisiones.

**11/10/2026 (Joel), segunda vuelta, después del Clean and Build con las correcciones de A, B
y C:**
- Franja naranja con clasificación E sin mora; confirmación al registrar E + mora "No"
  (Cancelar no guarda) — se mantiene.
- Consulta nueva con mora registrada después del envío: la franja pasa a roja, la tabla muestra
  las dos consultas y la marca "Registrada después del envío" en la nueva — hallazgo A
  confirmado y corregido.
- Refuerzo al tocar Aprobar con mora: "El buró indica mora. ¿Aprobar igualmente?" — OK.
- Rechazo de SOL-2026-0004 (motivo "Informconf: figura con deudas"): la auditoría trae
  "Consulta(s) vista(s) al decidir: [id 3, clasificación E, mora Sí]" — **la re-vinculación
  queda confirmada** (era la única parte de A sin cobertura de JUnit, sección 9).
- El caché quedó resuelto: antes del Ctrl+Shift+R no se veían los cambios (hallazgo B); con
  `VersionDespliegue` centralizado, no hizo falta repetirlo en esta vuelta.

**11/10/2026 (Joel), tercera vuelta, después del Clean and Build, junto con RF09/RF10:**
- (a) Tabla del buró con hora: "10/10/2026 10:45" — confirmado.
- (b) Badge "Registrada después del envío" en gris (no verde) — confirmado.
- (c) Botón "Sí, aprobar" / "Sí, rechazar" con texto propio (en vez de "Sí") — confirmado en la
  aprobación de SOL-2026-0001 y en el rechazo bloqueado de la prueba de dos pestañas.
- (d) Aviso "Revise antes de decidir" oculto en una solicitud ya decidida — confirmado (no
  volvió a aparecer en SOL-2026-0001 ya aprobada).

Las 4 mejoras menores quedan **probadas en pantalla, completas**.

## 12. Pendientes y trabajo futuro

- ~~Mejoras menores a-d encontradas en la prueba de pantalla del 11/10/2026~~ — **implementadas
  y probadas en pantalla el 11/10/2026**, ver sección 9 ("Decisiones del 11/10/2026, tercera
  vuelta") y sección 11. Sin pendientes.
- Re-vinculación al decidir (A, punto 4): confirmada en pantalla (sección 11) — sin pendientes.
- ~~Cuando se abra el documento de RF09/RF10, referenciar ahí el cambio de la re-vinculación~~ —
  **hecho** el 11/10/2026: [RF09_aprobar.md](RF09_aprobar.md) §2 y
  [RF10_rechazar.md](RF10_rechazar.md) §2 ya referencian este documento en vez de repetir el
  análisis. `RF10_rechazar.md` §6.2 también tiene el análisis completo de los motivos de
  rechazo EDAD/ZONA/POLITICA_LABORAL.
- **PDF del resumen para la carpeta física del cliente** (propuesta 3, decisión de Joel el
  11/10/2026: **trabajo futuro, no implementado todavía**): un informe nuevo (mismo patrón que
  `informe_aprobaciones.jasper`, pero de una sola solicitud) con cliente
  (edad/zona/situación laboral/empleador/deudas), condiciones del préstamo, afectación,
  consultas al buró y la decisión final si ya está tomada. Coordinar con
  Joel el diseño exacto antes de crear el `.jrxml` (qué columnas, si lleva membrete, si incluye
  el checklist de RF07/RF06) — no se diseña ningún reporte sin su confirmación explícita
  (`REGLAS_DESARROLLO.md` §6).
- Si en el futuro Joel pide el mismo aviso de mora/riesgo alto en `solicitudes/ficha.jsp` (la
  ficha del oficial, antes de enviar) — hoy esa ficha no muestra ningún aviso de este tipo —
  reutilizar `ServicioAprobacionCredito.construirAvisoBuro` (ya es pura y no depende de nada
  específico de la ficha del gerente) en vez de duplicar la lógica.

## 13. Guion para la defensa

1. Abrir la ficha de una solicitud `PENDIENTE_APROBACION` sin mora ni riesgo alto — no aparece
   ninguna franja arriba de la card "Cliente". Señalar, sin bajar la pantalla, los datos del
   cliente (edad, zona, situación laboral, empleador, otras deudas) en esa primera card.
2. Bajar a la card de condiciones y mostrar cada dato financiero con su ⓘ del glosario, y la
   fila de afectación resaltada en color.
3. Bajar a la card del buró y mostrar la consulta del cliente.
4. **Caso con mora, para armar en `sgcpc_db_pruebas` por pantalla (sin tocar la base
   directamente)**:
   1. Elegir o crear un cliente con el expediente completo (cédula vigente, comprobante de
      ingresos) y la autorización de consulta al buró ya firmada y cargada (`clientes/{id}`,
      pasos ① y ②).
   2. En el mismo cliente, paso ③ "Consulta al buró": cargar el informe de Informconf (el
      archivo que ya exige el paso B), completar Clasificación (dejarla sin clasificación o en
      A/B/C para este caso, ver el punto 6 para D/E) y tocar **"Sí"** en "¿Tiene deudas en
      mora?", después "Registrar consulta". Esto deja una `ConsultaCrediticia` con
      `indicador_mora = true`, todavía sin `id_solicitud` (se vincula recién en el paso 4).
   3. Paso ④: crear una solicitud nueva para ese cliente (monto/plazo que cumplan la política,
      para no mezclar este caso con un rechazo automático por otro motivo).
   4. Enviar la solicitud a aprobación (RF06) — ahí `ServicioSolicitudes` vincula la consulta ya
      registrada a esta solicitud (`ConsultaCrediticiaDAO.vincularSolicitud`).
   5. Entrar como gerente o administrador a `/aprobaciones`, abrir esa solicitud — debe
      aparecer, **arriba de todo, antes de la card "Cliente"**, la franja roja "El buró indica
      mora: (persona) — Informconf (fecha)."; también la alerta roja dentro de la card del
      buró, y el botón "Aprobar" debe pedir la confirmación reforzada.
5. **Caso de riesgo alto (D/E) sin mora**, repitiendo los pasos 1-4 con otro cliente, pero en el
   paso ③ elegir clasificación "D" o "E" y tocar **"No"** en mora — con esa combinación, antes
   de guardar, debe aparecer el SweetAlert2 "La clasificación (letra) suele indicar atrasos.
   ¿Confirma que Informconf no informa mora?"; confirmar con "Sí, no hay mora" para completar el
   registro. En `/aprobaciones`, esa solicitud debe mostrar la franja **naranja** "Riesgo alto
   según Informconf: clasificación (letra) — (persona)." arriba de todo (no la roja), y el
   botón "Aprobar" debe pedir su propia confirmación reforzada ("Informconf indica clasificación
   de riesgo alto...").
6. **Caso con las dos cosas a la vez** (mora Y clasificación D/E, mismo cliente): en el paso ③,
   elegir clasificación D o E y tocar "Sí" en mora (sin confirmación, el aviso de la
   confirmación es solo para "No"). En `/aprobaciones`, debe aparecer **solo la franja roja**,
   pero con la clasificación incluida en el texto: "El buró indica mora (clasificación X):
   (persona) — Informconf (fecha)." — nunca las dos franjas juntas.
