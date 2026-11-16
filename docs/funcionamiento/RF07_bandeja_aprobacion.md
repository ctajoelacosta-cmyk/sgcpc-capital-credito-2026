# RF07 — Bandeja de aprobación (pendientes y historial)

**Estado: cerrado y probado en pantalla completo el 11/10/2026 — tag `rf07-probado`.** Las 6
decisiones del 10/10/2026 (columna "Esperando", afectación a 1 decimal, checklist filtrado por
estado, detalle en bandeja vacía, CANCELADA en el informe, filtros + columnas de decisión en
Historial, tarjeta del Panel movida a "Hoy") y los 3 ajustes de texto/formato del 11/10/2026
(tiempo promedio solo con aprobadas/rechazadas, "—" en fecha de envío vacía, botón "Ver" en
Historial) quedaron probados por Joel en pantalla, además del bug de NullPointerException del
Historial con SOL-2026-0003, también corregido y probado.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF07 (módulo Aprobación):

> El sistema debe mostrar al gerente las solicitudes pendientes de aprobación en orden de
> llegada y el historial de las solicitudes ya decididas.

RF07 depende de RF06 (quién llega a la bandeja) y es la puerta de entrada a RF08 (resumen de
la solicitud), RF09 (aprobar) y RF10 (rechazar) — ver sección 5.

## 2. Cómo funciona

### 2.1 Pendientes, en orden de llegada

`SolicitudDAO.listarPendientesAprobacion` (`src/main/java/.../dao/SolicitudDAO.java:250`):

```sql
SELECT ... FROM solicitudes_credito WHERE estado_solicitud = 'PENDIENTE_APROBACION'
ORDER BY fecha_envio_aprobacion
```

Ordena por `fecha_envio_aprobacion` (el momento en que el oficial la envió a aprobación, RF06),
no por `fecha_solicitud` (cuándo se creó) — **cumple** el "orden de llegada" literal del texto:
dos solicitudes creadas el mismo día pero enviadas a aprobación en momentos distintos (una
quedó esperando documentos, la otra se envió directa) aparecen en el orden en que realmente
llegaron a la bandeja del gerente, no en el orden en que nacieron.

Columnas que muestra `aprobaciones/lista.jsp` (pestaña Pendientes): Código, Enviada (fecha,
sin hora — `FormateadorFechas.comoDdMmAaaa(fila.getFechaEnvioAprobacion().toLocalDate())`),
Cliente, Monto solicitado, Plazo, Afectación (`FormateadorNumeros.comoPorcentaje`, con todos
los decimales — no tiene el ajuste a un decimal que RF06 ya aplicó en la ficha de la solicitud
y en la ficha de aprobación), botón "Revisar" (va a la ficha, RF08).

**No cumple** — no muestra cuánto tiempo lleva esperando (ni como columna "X días", ni como
alguna marca visual tipo "hace 3 días"). El dato para calcularlo ya existe
(`fecha_envio_aprobacion`, comparado con la hora actual) pero no hay ningún cálculo ni columna
para esto hoy, ni en Java ni en la JSP.

### 2.2 Historial de decididas

`SolicitudDAO.listarDecididas` (línea 267):

```sql
SELECT ... FROM solicitudes_credito
WHERE estado_solicitud IN ('APROBADA', 'RECHAZADA', 'CANCELADA')
ORDER BY COALESCE(fecha_decision, fecha_cancelacion) DESC
```

**Cumple** con los tres estados que pregunta Joel: incluye APROBADA, RECHAZADA y CANCELADA
(la cancelación del gerente desde la ficha de aprobación, RF06, cuenta — usa la misma columna
`fecha_cancelacion` que la cancelación del oficial). Más recientes primero.

Columnas en `aprobaciones/lista.jsp` (pestaña Historial, `?vista=historial`): las mismas que
Pendientes más Estado (badge). **No cumple** — la tabla del historial no muestra quién decidió,
cuándo exactamente (hora) ni el motivo; eso solo aparece si se entra a la ficha de cada
solicitud (`aprobaciones/ficha.jsp`, franja de color con "Aprobada/Rechazada/Cancelada por
[nombre] el [fecha y hora]. Motivo: ..."; ver `ControladorAprobacionesServlet.mostrarFicha`,
líneas 204-210, y RF06 §2). El texto del anteproyecto no distingue "en la lista" de "en el
detalle", así que esto puede leerse como cumplido (el dato existe y se ve, con un clic más) o
como una limitación de la lista — queda para que Joel decida si alcanza o si la lista también
debería traer esas columnas.

**No cumple** — la lista (`aprobaciones/lista.jsp`) no tiene filtros de fecha, estado ni
cliente, ni exporta a PDF ni a Excel/CSV. Eso sí existe, pero en otra pantalla:
`/reportes/aprobaciones` ("Informe de Aprobaciones y rechazos", Bloque 7/E10):

- Filtro: solo rango de fecha (`FiltroAprobacionesReporte.desde/hasta`, sobre
  `fecha_decision`) — **sin filtro de estado ni de cliente**, a diferencia de lo que pregunta
  Joel.
- Incluye **solo APROBADA y RECHAZADA** (`SolicitudDAO.listarDecididasPorRangoFecha`, línea
  351): el comentario del código dice explícitamente "nunca CANCELADA -- esa la cancela el
  propio oficial/cliente, no es una 'decisión' del gerente". Esa razón ya no es exacta desde
  RF06: ahora el **gerente también puede cancelar** una solicitud desde la ficha de aprobación
  (`ServicioAprobacionCredito.cancelar`), así que el informe se queda sin una parte de las
  cancelaciones que sí pasan por la bandeja del gerente.
- Exporta a PDF (`informe_aprobaciones.jasper`, vista "inline") y a CSV (`aprobaciones.csv`,
  mismo criterio que el resto de los informes del sistema — no hay exportación a `.xlsx` en
  ningún informe, CSV es el equivalente que se usa en todo el sistema).
- Columnas del informe: número, cliente, monto solicitado, monto aprobado, decisión
  (Aprobada/Rechazada), decidido por, fecha de envío, fecha de decisión, tiempo de respuesta
  (minutos, formateado), motivo (solo si fue rechazada).

Es decir: el filtro por fecha y la exportación **sí existen en el sistema**, pero en una
pantalla de informes separada de la bandeja operativa, pensada para analizar un período ya
cerrado (y hoy sin CANCELADA, sin filtro de estado/cliente) — no es lo mismo que "la bandeja
tiene filtros". Si el anteproyecto exige que sea la propia bandeja la que filtre y exporte,
esto no cumple todavía.

### 2.3 Quién puede verla

`FiltroAutorizacion` protege `/aprobaciones` con el permiso `APROBAR_RECHAZAR_SOLICITUD`
(línea 50). Matriz en `ServicioAutorizacion.construirMatriz()`:

| Perfil | `APROBAR_RECHAZAR_SOLICITUD` |
|---|---|
| Gerente (`GERENTE_ADMINISTRADOR`) | TOTAL |
| Administrador | TOTAL (regla D7: el administrador recibe TOTAL en todos los permisos) |
| Oficial de crédito | NINGUNO |
| Cajero | NINGUNO |

**Cumple**: gerente y administrador pueden entrar; oficial y cajero están bloqueados **en el
servidor** (no solo ocultando el enlace del menú) — un oficial o cajero que fuerce la URL
`/aprobaciones` recibe el bloqueo de `FiltroAutorizacion`, verificado por la matriz, igual que
se verificó para RF06.

Matiz encontrado (no es un incumplimiento de RF07, pero vale anotarlo): el permiso
`VER_REPORTES` que protege `/reportes` (incluido `/reportes/aprobaciones`) da **CONSULTA** al
oficial (no NINGUNO) y **NINGUNO** al cajero. Es decir, el oficial no puede entrar a la bandeja
de aprobación, pero sí puede consultar el informe de Aprobaciones y rechazos (otra pantalla,
ya decidido en Bloque 7/E10, no parte de este RF). Se lo deja anotado para que Joel confirme si
eso es lo que quiere.

### 2.4 Panel — tarjeta de pendientes (nota del RF06)

Revisando el pedido anotado en `RF06_envio_aprobacion.md` §10 ("una tarjeta en el Panel del
gerente y del administrador: 'N solicitudes pendientes de aprobación → Ir a Aprobaciones'"):
**ya existe y ya cumple**, no hace falta construirla.

- `panel.jsp` líneas 227-241: tarjeta "Solicitudes pendientes de aprobar" con el número
  `indicadoresPanel.getSolicitudesPendientesAprobar()` y el enlace "Ir a Aprobaciones →",
  visible cuando `esGerentePanel` es verdadero.
- `esGerentePanel` (línea 28-29) ya incluye a `GERENTE_ADMINISTRADOR` **y** a `ADMINISTRADOR`
  (regla D7, "el administrador ve lo mismo que el gerente en el panel") — así que la tarjeta
  **ya la ve el administrador también**, no hace falta agregar nada ahí.
- El número es dinámico: `ServicioPanel` (línea 79) lo calcula con
  `solicitudDAO.listarPendientesAprobacion(conexion).size()` en cada carga del panel, no es un
  valor fijo ni cacheado.

Cuando se escribió esa nota (10/10/2026, durante el cierre de RF06) no se había revisado
todavía `panel.jsp` — el hallazgo de este análisis es que el pedido ya estaba resuelto antes de
anotarse, probablemente construido en el Bloque 4/E8 junto con el resto de `IndicadoresPanel`.

### 2.5 Checklist de la ficha filtrado por estado (nota del RF06)

La segunda nota de `RF06_envio_aprobacion.md` §10 sí sigue pendiente — **no cumple todavía**.

`aprobaciones/ficha.jsp` línea 97: `<% if (checklistClienteRevision != null) { %>` — el
checklist se muestra siempre que no sea null, sin mirar el estado de la solicitud.
`ControladorAprobacionesServlet.mostrarFicha` (línea 199) llama
`servicioSolicitudes.construirChecklistPrevioEnvio(conexion, cliente)` sin condición alguna,
para cualquier estado.

`construirChecklistPrevioEnvio` (`ServicioSolicitudes.java:284`) es una validación **en vivo**:
recalcula contra la política y los datos del cliente vigentes en este momento (edad, zona,
situación laboral, cédula vigente, comprobante de ingresos, autorización de buró vigente,
consulta de buró registrada) — no es una foto de cómo estaban las cosas cuando se decidió la
solicitud. Es exactamente el mismo problema que RF06 §7 punto 3 ya corrigió para
`solicitudes/ficha.jsp` (`esBorrador`, para no mostrar advertencias de validación en vivo sobre
una solicitud que ya no se puede editar): una solicitud **APROBADA** hace tres meses puede
mostrar hoy una cruz roja en "Edad dentro de la política" si la política cambió después, dando
la impresión de que algo está mal con una decisión que en su momento fue correcta.

## 3. Auditoría

RF07 en sí mismo no genera eventos de auditoría nuevos — es una pantalla de **lectura**
(mostrar pendientes y decididas). Los eventos que la bandeja y la ficha exhiben ya los
registran los RF que sí cambian algo:

- `ENVIAR_SOLICITUD_APROBACION` (RF06, `ServicioSolicitudes`) — por qué una solicitud entra a
  la pestaña Pendientes.
- `APROBAR_SOLICITUD`, `RECHAZAR_SOLICITUD`, `CANCELAR_SOLICITUD` (RF09/RF10/RF06,
  `ServicioAprobacionCredito`) — por qué una solicitud pasa a la pestaña Historial.
- `auditarExportacionCsvAprobaciones` (`ServicioInformes`) — si se exporta el informe de
  `/reportes/aprobaciones` a CSV, eso sí se auditoría (quién, con qué filtro de fecha, cuántas
  filas), pero es del informe, no de la bandeja de RF07.

No se encontró ningún evento de auditoría para "el gerente abrió la bandeja" o "el gerente
abrió la ficha de la solicitud X" (una simple lectura no auditada, igual que el resto de las
pantallas de consulta del sistema) — consistente con el resto del sistema, no es un hallazgo.

## 4. Dónde está

- **Modelo**: `SolicitudCredito` (estado, `fechaEnvioAprobacion`, `fechaDecision`,
  `decididoPor`, `motivoRechazo`, `fechaCancelacion`, `canceladoPor`, `motivoCancelacion`).
- **DAO**: `SolicitudDAO.listarPendientesAprobacion` / `listarDecididas` /
  `listarDecididasPorRangoFecha`.
- **Servicio**: `ServicioAprobacionCredito.listarPendientes` / `listarDecididas` /
  `obtenerPorId` / `listarConsultasDeLaSolicitud` / `listarMotivosRechazo`; `ServicioPanel`
  (tarjeta); `ServicioReportes.listarInformeAprobaciones` / `calcularTotalesAprobaciones`
  (informe separado); `ServicioSolicitudes.construirChecklistPrevioEnvio` (checklist, en vivo).
- **Controlador**: `ControladorAprobacionesServlet` (`mostrarLista`, `mostrarFicha`);
  `ControladorReportesServlet` (`mostrarInformeAprobaciones`, `generarInformeAprobacionesPdf`,
  `exportarAprobacionesCsv`, caso `"aprobaciones"` del dispatch).
- **Vistas**: `aprobaciones/lista.jsp` (bandeja, dos pestañas), `aprobaciones/ficha.jsp`
  (resumen y decisión, RF08/09/10), `reportes/informe-aprobaciones.jsp` (informe separado),
  `panel.jsp` (tarjeta).
- **Permisos**: `Permiso.APROBAR_RECHAZAR_SOLICITUD` (bandeja y ficha), `Permiso.VER_REPORTES`
  (informe), ambos en `FiltroAutorizacion` y `ServicioAutorizacion`.

## 5. Dependencias

- **RF06** (envío a aprobación / cancelación): es la fuente de las filas de la bandeja —
  `fecha_envio_aprobacion` (orden de Pendientes) y una de las tres formas de llegar a
  Historial (CANCELADA). Un cambio en cómo o cuándo se marca `PENDIENTE_APROBACION` afecta
  directamente qué aparece en RF07.
- **RF08** (resumen de la solicitud): es lo que se ve al abrir la ficha desde la bandeja
  (botón "Revisar") — RF07 es la puerta de entrada, RF08 es el contenido de esa puerta.
- **RF09** (aprobar) y **RF10** (rechazar): las acciones que sacan una solicitud de Pendientes
  y la llevan a Historial — viven en la misma ficha (`aprobaciones/ficha.jsp`) y en el mismo
  servicio (`ServicioAprobacionCredito`) que RF07 ya usa para listar.
- Un cambio en RF07 (por ejemplo, agregar filtros a la bandeja) no debería afectar a RF06,
  RF08, RF09 ni RF10 en sentido contrario — es una pantalla de lectura sobre datos que esos
  otros RF ya escriben.

## 6. Análisis

### Análisis del 10/10/2026 (este documento)

Hecho a pedido de Joel, revisando el texto firmado de RF07 contra el código real, sin cambiar
nada. Resultado resumido en la tabla de la sección 7.

## 7. Tabla de cumplimiento

| Parte del texto / pregunta de Joel | Cumple | Dónde / detalle |
|---|---|---|
| Pendientes en orden de llegada (por fecha de envío, no de creación) | **Sí** | `SolicitudDAO.listarPendientesAprobacion`, `ORDER BY fecha_envio_aprobacion` |
| Columnas de Pendientes | Parcial | Código, Enviada (solo fecha), Cliente, Monto, Plazo, Afectación (sin redondeo a 1 decimal) — `aprobaciones/lista.jsp` |
| Tiempo esperando (Pendientes) | **No** | No existe ningún cálculo ni columna; el dato base (`fecha_envio_aprobacion`) sí está disponible |
| Historial: incluye aprobada/rechazada/cancelada | **Sí** (la lista) | `SolicitudDAO.listarDecididas`, `IN ('APROBADA','RECHAZADA','CANCELADA')` |
| Historial: quién decidió, cuándo, motivo | Parcial | No está en la fila de la lista; sí en la ficha de cada solicitud (un clic más) |
| Historial: filtros (fecha/estado/cliente) en la bandeja | **No** | `aprobaciones/lista.jsp` no tiene filtros propios |
| Historial: exporta a PDF/Excel desde la bandeja | **No** (existe en otra pantalla) | `/reportes/aprobaciones`: filtro solo por fecha, sin estado/cliente, excluye CANCELADA, exporta PDF+CSV |
| Quién puede verla: gerente y administrador | **Sí** | `APROBAR_RECHAZAR_SOLICITUD` = TOTAL para gerente y administrador |
| Oficial y cajera bloqueados en el servidor | **Sí** | `APROBAR_RECHAZAR_SOLICITUD` = NINGUNO para oficial y cajero, verificado por `FiltroAutorizacion` |
| Tarjeta en el Panel del gerente y del administrador (nota RF06) | **Sí, ya existe** | `panel.jsp` líneas 227-241, `esGerentePanel` incluye administrador (D7), número dinámico |
| Checklist de la ficha filtrado por estado (nota RF06) | **No** | `aprobaciones/ficha.jsp` línea 97, sin condición de estado — mismo problema ya corregido en `solicitudes/ficha.jsp` |
| Bandeja vacía: mensaje que diga qué sigue | Parcial | "No hay solicitudes pendientes de aprobación." / "Todavía no hay solicitudes decididas." — informa, pero no dice explícitamente qué hacer a continuación |

**Tabla de arriba: foto del 10/10/2026, antes de las decisiones.** Después de implementar las 6
decisiones de la sección 9 y los 3 ajustes del 11/10/2026 (todo probado en pantalla, sección
11), todos los "No" y "Parcial" de arriba pasaron a **Sí**: tiempo esperando (columna
"Esperando"), filtros de Historial (estado + rango de fechas en la propia bandeja), quién
decidió/cuándo/motivo también en la lista (columnas "Decisión"/"Decidida por"/"Fecha de
decisión", no solo en la ficha), checklist filtrado por estado, y bandeja vacía con el detalle
de qué sigue. El export a PDF/Excel sigue viviendo en `/reportes/aprobaciones` (no se duplicó en
la bandeja) — la bandeja agrega un enlace directo ("Exportar en Informes") en vez de repetir los
botones de PDF/CSV.

## 8. Propuestas (para que Joel decida)

1. **Columna "Esperando hace..."** en Pendientes: calcular con `Duration.between` sobre
   `fecha_envio_aprobacion` y mostrar algo como "2 días" o "3 h" (umbral a definir — por ejemplo
   menos de 24 h en horas, de ahí en días). Puede ir en Java (un método puro, fácil de probar)
   o directamente en la JSP con los formateadores existentes.
2. **Afectación con 1 decimal** en `aprobaciones/lista.jsp`, igual que ya se hizo en RF06 para
   las dos fichas (`comoPorcentajeUnDecimal`) — mismo criterio, para no tener dos formatos
   distintos del mismo dato en pantallas vecinas.
3. **Filtrar el checklist de `aprobaciones/ficha.jsp` por estado**: mostrarlo solo cuando
   `pendiente` es verdadero (mismo patrón que RF06 ya aplicó a `esBorrador` en
   `solicitudes/ficha.jsp`), ocultándolo en aprobada/rechazada/cancelada — para no confundir una
   validación en vivo con el estado real de una solicitud ya decidida.
4. **Mensaje de "bandeja vacía" con qué sigue**: por ejemplo, en Pendientes vacío, agregar algo
   como "No hay solicitudes esperando su decisión." (ya está bien) — si Joel quiere, se puede
   sumar una frase que oriente (ej. invitar a revisar el Historial), aunque esta pantalla no
   tiene una acción siguiente obvia distinta de "esperar a que lleguen más" — a definir con
   Joel si hace falta agregar algo o si el mensaje actual ya alcanza.
5. **CANCELADA en el informe de `/reportes/aprobaciones`**: si Joel quiere que el informe
   también cuente las cancelaciones del gerente (ya que ahora sí puede cancelar desde RF06), hay
   que decidir si se agregan como una tercera categoría en los totales o se dejan fuera a
   propósito (el informe se llama "Aprobaciones y rechazos", no incluye cancelaciones por
   diseño) — queda pendiente de que Joel lo confirme, no se toca sin su decisión.
6. **Filtros/export directamente en la bandeja** (si el anteproyecto lo exige en esta pantalla
   y no alcanza con el informe separado): agregar al menos filtro de fecha y de cliente a
   `aprobaciones/lista.jsp`, pestaña Historial, o aceptar que `/reportes/aprobaciones` ya cubre
   ese requisito desde otra pantalla — a decidir con Joel antes de tocar código.

## 9. Decisiones

Decisiones del 10/10/2026 (Joel), sobre las 6 propuestas de la sección 8 — todas implementadas:

1. **Columna "Esperando hace..."**: se agrega a Pendientes como "Esperando" (ej. "2 h", "1
   día"), contada desde `fecha_envio_aprobacion` hasta el momento de mirar la pantalla
   (`Reloj.ahora()`). Implementación: `FormateadorFechas.comoDuracion(long minutos)` (pura,
   nueva), reutilizada por `ServicioReportes.formatearDuracionMinutos` (que antes tenía la
   misma lógica duplicada, ahora delega) para no mantener el cálculo en dos lugares.
2. **Afectación con 1 decimal**: `aprobaciones/lista.jsp` pasó de
   `FormateadorNumeros.comoPorcentaje` a `comoPorcentajeUnDecimal`, mismo criterio que las
   fichas de solicitud y de aprobación (RF06).
3. **Checklist de `aprobaciones/ficha.jsp` filtrado por estado**: en vez de condicionar en la
   JSP (como hizo RF06 con `esBorrador` en `solicitudes/ficha.jsp`), se condicionó en
   `ControladorAprobacionesServlet.mostrarFicha` — el checklist (`construirChecklistPrevioEnvio`,
   una validación en vivo) solo se calcula y se pone en el `request` cuando la solicitud sigue
   `PENDIENTE_APROBACION`; en aprobada/rechazada/cancelada el atributo queda `null` y la JSP (que
   ya tenía `if (checklistClienteRevision != null)`) deja de mostrarlo sin necesidad de tocarla.
   Las condiciones (tabla de monto/plazo/TEA/TNA/CFT/total a pagar/afectación) y la franja de
   decisión (quién, cuándo, motivo) siguen mostrándose siempre, sin cambios — igual que RF06.
4. **Bandeja vacía (Pendientes)**: el texto pasa a ser "No hay solicitudes pendientes de
   aprobación." más el detalle "Cuando un oficial envíe una solicitud, aparecerá aquí en orden
   de llegada." (en una segunda línea, con `<small>`). El vacío de Historial no cambió — Joel no
   pidió un detalle ahí.
5. **CANCELADA en `/reportes/aprobaciones`**: se agrega como tercera categoría. Cambios:
   `SolicitudDAO.listarDecididasPorRangoFecha` ahora incluye `'CANCELADA'` y filtra por
   `COALESCE(fecha_decision, fecha_cancelacion)` (las canceladas no tienen `fecha_decision`);
   `ServicioReportes.listarInformeAprobaciones` usa `fecha_cancelacion`/`cancelado_por`/
   `motivo_cancelacion` cuando corresponde, y la columna "Decisión" pasa a usar
   `estado.getEtiqueta()` en vez de un `if` binario Aprobada/Rechazada (la etiqueta del enum ya
   dice "Cancelada"); `TotalesAprobaciones` tiene ahora `cantidadCanceladas`/`montoCanceladas`
   (monto = monto **solicitado**, igual que rechazadas, porque una cancelada nunca tiene monto
   aprobado); `informe_aprobaciones.jrxml` y `reportes/informe-aprobaciones.jsp` muestran la
   tercera categoría en el resumen/pie de página. El CSV no necesitó cambios (ya usaba los
   mismos campos genéricos).
6. **Filtros y export en la propia bandeja**: Historial (`aprobaciones/lista.jsp`) ahora tiene
   un formulario con **Estado** (Todas/Aprobada/Rechazada/Cancelada) y **Desde/Hasta** (rango
   sobre la fecha de la decisión), resuelto por
   `SolicitudDAO.listarDecididasConFiltro`/`ServicioAprobacionCredito.listarDecididasConFiltro`
   (mismo criterio que `listarConFiltros` de Solicitudes). El export en sí (PDF/CSV) **no se
   duplica** en la bandeja: se agrega un enlace "Exportar en Informes", que lleva a
   `/reportes/aprobaciones` llevando el mismo rango de fechas elegido (el informe no tiene
   filtro de estado, así que ese valor no se traslada).
   Además, Historial ahora muestra las columnas **Decisión**, **Decidida por** y **Fecha de
   decisión** (antes solo estaban en la ficha, no en la lista) — "Decidida por" resuelve
   `decididoPor` o `canceladoPor` según corresponda, con un `Map<Long, Usuario>` armado una sola
   vez por página (mismo patrón que `clientesPorSolicitud`).

**Tarjeta del Panel** (hallazgo adicional de Joel, no una de las 6 propuestas): no apareció en
su Panel de las 19:03 como administrador. Revisado el código: no se encontró ningún error en la
condición (`esGerentePanel` ya incluye `ADMINISTRADOR` desde D7, y el número
`getSolicitudesPendientesAprobar()` ya es dinámico) — la causa más probable es la **ubicación**:
la tarjeta estaba en el bloque "Pendientes", el tercero de la página (después de "Hoy" y
"Cartera"), así que había que bajar bastante para verla. Decisión de Joel: moverla a "Hoy"
(arriba de todo), para gerente y administrador — hecho en `panel.jsp`. Si el problema hubiera
sido otra cosa (una sesión vieja, por ejemplo), este cambio de ubicación no lo habría resuelto;
queda para que Joel confirme en la próxima prueba en pantalla que ahora sí se ve.

**Error encontrado después del Clean and Build (11/10/2026)**: al abrir
`/aprobaciones?vista=historial`, la tabla se dibujaba hasta la fila de SOL-2026-0003
(cancelada) y ahí aparecía "Ocurrió un error inesperado" incrustado dentro de la tabla.

- **Log** (`C:\tomcat9\logs\localhost.2026-10-11.log`, 00:06:07): `java.lang.NullPointerException:
  Cannot invoke "java.time.LocalDateTime.toLocalDate()" because the return value of
  "py.com.capitalcredito.sgcpc.modelos.SolicitudCredito.getFechaEnvioAprobacion()" is null`, en
  `aprobaciones/lista.jsp` línea 108 (`fila.getFechaEnvioAprobacion().toLocalDate()`, la columna
  "Enviada"), disparado desde `ControladorAprobacionesServlet.mostrarLista` (línea 171).
- **Causa real** (no la que se sospechaba): no fueron las columnas nuevas "Decidida por"/"Fecha
  de decisión" — esas ya resolvían bien el caso cancelado (usan `fecha_cancelacion`, que
  `SolicitudDAO.marcarCancelada` siempre completa). El problema es anterior a esas columnas: la
  columna "Enviada" (que ya existía antes de este RF) asume que toda fila de Historial tiene
  `fecha_envio_aprobacion`. SOL-2026-0003 es CANCELADA pero **nunca se envió a aprobación** —
  se canceló directo desde BORRADOR (ver `solicitudes/ficha.jsp`, el botón "Cancelar solicitud"
  está disponible mientras `esBorrador`, no solo en `PENDIENTE_APROBACION`) — así que su
  `fecha_envio_aprobacion` queda `null` en la base, y tanto la columna "Enviada" como la columna
  nueva "Esperando" (que usa `Duration.between` sobre esa misma fecha) podían romper con ese
  dato. Este mismo riesgo ya existía en el informe `/reportes/aprobaciones` desde que se le
  agregó CANCELADA (decisión 5 de esta sección) — se corrigió ahí también, de modo preventivo
  (`ServicioReportes.listarInformeAprobaciones`, ver más abajo).
- **Corrección**: se creó `FilaBandejaAprobacion` (DTO nuevo) y
  `ServicioAprobacionCredito.construirFilaBandeja`, que arma y formatea **cada fila entera en el
  controlador**, antes de llegar a la JSP — ningún getter de `FilaBandejaAprobacion` puede
  devolver `null` (una fecha faltante se resuelve a `"-"` ahí mismo). `lista.jsp` ya no hace
  ningún `.toLocalDate()` ni `Duration.between()` propio: solo imprime los getters de la fila.
  Esto responde también al pedido de prevención: si un dato llegara a faltar en el futuro, el
  cálculo se haría (y fallaría, si tuviera que fallar) en el controlador, dentro del `try` que ya
  maneja `SQLException` con el mensaje de error estándar — nunca a mitad de una fila ya enviada
  al navegador.
- **Corrección preventiva relacionada**: `ServicioReportes.listarInformeAprobaciones` tenía el
  mismo riesgo (`Duration.between(solicitud.getFechaEnvioAprobacion(), ...)` sin chequear null)
  desde que la decisión 5 de esta sección agregó CANCELADA al informe de `/reportes/aprobaciones`
  — una solicitud cancelada-desde-borrador en el rango de fechas elegido lo habría roto también.
  Se corrigió del mismo modo: si no hay `fecha_envio_aprobacion`, el tiempo de respuesta queda en
  "—" (0 minutos por dentro, pero ya no entra al promedio -- ver el ajuste siguiente).
- **Pruebas agregadas**: `ServicioAprobacionCreditoTest` —
  `testConstruirFilaBandejaCanceladaSinEnvioAAprobacion` (el caso que rompía, sin
  `fecha_envio_aprobacion`), `testConstruirFilaBandejaAprobadaEnHistorial`,
  `testConstruirFilaBandejaRechazadaEnHistorial` y
  `testConstruirFilaBandejaPendienteCalculaEsperando`.

**Tres ajustes de texto y formato, probados por Joel el 11/10/2026 después del Clean and Build
que sí incluyó la corrección del NullPointerException:**

1. **"Tiempo promedio" del informe, solo con aprobadas y rechazadas**: una cancelada no tiene un
   "tiempo de respuesta" real que promediar (si nunca se envió, ya queda en 0/"—"; si sí se
   envió, el tiempo hasta la cancelación no es una "respuesta" del gerente). Corregido en
   `ServicioReportes.calcularTotalesAprobaciones`: la suma y el contador del promedio ya no
   incluyen las canceladas. Si no queda ninguna aprobada ni rechazada en el período, el promedio
   dice "—" en vez de "0 min". Prueba agregada:
   `testCalcularTotalesAprobacionesSoloCanceladasPromedioGuion` (y se extendió
   `testCalcularTotalesAprobacionesConCanceladas` para confirmar el nuevo cálculo: promedio de
   (1440 + 165) / 2 = 802 min, sin la cancelada).
2. **"Fecha de envío" vacía del informe, "—" en vez de en blanco**: mismo criterio que
   `FECHA_HORA_CIERRE` en el informe de Caja (`ServicioReportes.listarInformeCaja`) — corregido
   en `listarInformeAprobaciones`, solo para esa columna (no se tocó
   `FormateadorFechas.comoDdMmAaaaHhMm`, que sigue devolviendo `""` para los demás usos del
   sistema que esperan ese comportamiento).
3. **Botón "Ver" en vez de "Revisar" en el Historial de la bandeja**: "Revisar" implica una
   acción pendiente (aprobar/rechazar/cancelar), que solo tiene sentido en Pendientes; en
   Historial la solicitud ya está decidida. Corregido en `aprobaciones/lista.jsp` (texto
   condicional por `vistaHistorial`), sin tocar el controlador ni la ruta.

## 10. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 14/11/2026 | **Tanda 2 de la revisión UX de Aprobaciones** (B175, B176). **B175**: la bandeja **pagina de 20** (fragmento compartido `plantilla/paginacion.jsp`) y **ordena por columna** — 7 claves en Pendientes, 9 en el Historial, cada pestaña con su lista blanca en `ServicioAprobacionCredito`; el contador pasa al **total**. El orden por defecto es el que ya traía el SQL (Pendientes por fecha de envío ascendente, Historial por fecha de decisión **descendente**), ahora declarado en el servicio y visible con su flecha. Se ordena el `SolicitudCredito` (no la fila formateada) y antes de cortar la página; *"Esperando"* no ordena (mismo dato que *"Enviada"*). **B176**: el enlace *"Exportar en Informes"* lleva las fechas **y el texto del buscador** (como `clienteUOperacion`); el estado no viaja porque el informe no tiene ese filtro. Ver `recorrido/FASE2_PLAN.md` § Aprobaciones | *(este commit)* | — |
| 14/11/2026 | **Tanda 1 de la revisión UX de Aprobaciones** (B170-B174). **B170**: el *"Volver a la bandeja"* conserva la **pestaña** y los filtros (mecanismo de sesión de B147/B163, con `vista` incluido). **B171**: *"Rechazar"* y *"Cancelar solicitud"* pasan de formularios desplegables a **ventanas**, con los mismos campos y los mismos `id` (los scripts del detalle obligatorio siguen intactos), sin `data-confirmar` y con el foco en *"No, volver"*, porque ninguna de las dos se deshace. **B172**: la pestaña dice **Pendientes (N)**. **B173**: *"Limpiar"*/*"Buscar"* a la fila del campo en Pendientes. **B174**: columna **Enviada por** en Pendientes (mapa `creado_por` a `Usuario`, una consulta por usuario distinto). Ver `recorrido/FASE2_PLAN.md` § Aprobaciones — tanda 1 | *(este commit)* | — |
| 11/10/2026 | Probado en pantalla completo: 6 decisiones + bug del Historial + 3 ajustes de texto/formato (tiempo promedio solo aprobadas/rechazadas, "—" en fecha de envío vacía, botón "Ver") | *(pendiente)* | `rf07-probado` |
| 11/10/2026 | Corregido el NullPointerException del Historial (SOL-2026-0003, cancelada sin fecha_envio_aprobacion) -- nuevo `FilaBandejaAprobacion`/`construirFilaBandeja` (fila formateada entera en el controlador), misma corrección preventiva en el informe de `/reportes/aprobaciones`, 4 pruebas nuevas | *(pendiente)* | — |
| 10/10/2026 | Implementadas las 6 decisiones de la sección 9 (columna "Esperando", afectación a 1 decimal, checklist filtrado por estado, detalle en bandeja vacía, CANCELADA en el informe, filtros + columnas de decisión en Historial, tarjeta del Panel movida a "Hoy") | *(pendiente)* | — |
| 10/10/2026 | Análisis completo contra el texto firmado, sin cambios de código | `6d4a7bf` | — |

## 11. Pruebas realizadas en pantalla

**11/10/2026 (Joel), después del primer Clean and Build con las 6 decisiones:**
- Pendientes con "Esperando: 5 h 20 min" y afectación "17,4 %".
- Tarjeta "1 · Solicitudes pendientes de aprobar" arriba en el Panel (bloque "Hoy"), para
  gerente y administrador.
- Error encontrado: NullPointerException en Historial al mostrar SOL-2026-0003 (cancelada sin
  fecha_envio_aprobacion) — ver el hallazgo de la sección 9. Corregido y confirmado por Joel en
  la misma vuelta de pruebas, sin un Clean and Build aparte.

**11/10/2026 (Joel), después del Clean and Build con la corrección del NullPointerException:**
- Historial sin error, con las canceladas, columna "Decidida por" y "Fecha de decisión".
- Informe de `/reportes/aprobaciones` con las canceladas y su total.
- Los 3 ajustes de texto/formato (tiempo promedio, "—" en fecha de envío, botón "Ver") quedaron
  pendientes de confirmar en el próximo Clean and Build (son cambios chicos, Joel los verifica
  después del commit).

## 12. Pendientes y trabajo futuro

- Ninguno — las 6 decisiones y los 3 ajustes quedaron implementados y probados en pantalla
  (sección 11). `solicitudes/ficha.jsp` ya tenía el mismo ⓘ del glosario desde RF06; su
  checklist (antes de enviar) no tiene el mismo problema porque esa ficha siempre se ve con la
  solicitud todavía editable (`esBorrador`), no decidida.
- **(20/10/2026, tanda de usabilidad, punto 6 paso 2)**: la bandeja ya tiene buscador con AJAX
  (`listado-vivo.js`), por N.º de solicitud, cédula o nombre del cliente -- en Pendientes y en
  Historial, combinado (nunca en reemplazo) con el estado/desde/hasta que Historial ya tenía.
  Sigue sin exportar ni filtrar por fecha en la propia bandeja de Pendientes (eso sigue siendo
  "No cumple" de la sección 2, sin cambios).
- Si en el futuro se agrega un cuarto estado decidido, o un filtro de cliente al informe de
  `/reportes/aprobaciones`, revisar `FilaInformeAprobacion`/`TotalesAprobaciones`/
  `FiltroAprobacionesReporte` (hoy sin ese filtro, por decisión explícita del Bloque 7/E10).

## 13. Guion para la defensa

1. Mostrar la bandeja con dos o tres solicitudes `PENDIENTE_APROBACION` enviadas en momentos
   distintos — señalar que el orden es por `fecha_envio_aprobacion`, no por la fecha de alta, y
   la columna "Esperando" (contada desde el envío).
2. Entrar a la pestaña Historial y mostrar una aprobada, una rechazada y una cancelada (por el
   gerente, y una cancelada directo desde BORRADOR sin pasar por aprobación, como
   SOL-2026-0003) — las tres aparecen juntas, con "Decisión", "Decidida por" y "Fecha de
   decisión" en la propia lista (sin tener que abrir cada ficha), ordenadas por fecha de
   decisión/cancelación. El botón dice "Ver" (no "Revisar", que ya no aplica a una decidida).
3. Filtrar el Historial por estado y por rango de fechas, y usar el enlace "Exportar en
   Informes" para llegar a `/reportes/aprobaciones` con el mismo rango.
4. Abrir la ficha de una decidida y señalar la franja con quién decidió, cuándo y el motivo —y
   que el checklist (validación en vivo) ya no se muestra ahí, solo en una `PENDIENTE_APROBACION`.
5. Intentar entrar a `/aprobaciones` logueado como oficial o como cajero — mostrar el bloqueo
   del servidor (403), no solo que el menú no tiene el enlace.
6. Mostrar la tarjeta del Panel ("N · Solicitudes pendientes de aprobar → Ir a Aprobaciones")
   arriba de todo, en "Hoy", con el gerente y con el administrador — mismo número, mismo enlace.
7. Mostrar el informe de `/reportes/aprobaciones` con las tres categorías (aprobadas, rechazadas
   y canceladas) y su "Tiempo promedio" (solo sobre aprobadas y rechazadas).
