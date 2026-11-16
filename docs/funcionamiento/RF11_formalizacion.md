# RF11 — Formalización: bandeja de aprobadas y fecha del primer vencimiento

**Estado: cerrado y probado en pantalla completo el 11/10/2026 — tag `rf11-probado`.** Los 3
hallazgos de la sección 6 quedaron corregidos y confirmados en pantalla (rango tras corrimiento,
CFT recalculado, 40 % revalidado, auditoría con el detalle completo), junto con el calendario
con min/max y el resumen antes de confirmar. Quedan 8 mejoras menores (sección 10, a-h), que NO
se hacen en esta vuelta.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF11 (módulo Formalización):

> El sistema debe mostrar las solicitudes aprobadas pendientes de formalizar y permitir fijar la
> fecha del primer vencimiento dentro del rango permitido, teniendo en cuenta los feriados y los
> fines de semana.

## 2. Cómo funciona

**Bandeja (pregunta a).** `GET /formalizacion` → `ControladorFormalizacionServlet.mostrarLista`
(`ControladorFormalizacionServlet.java:335-384`) → `ServicioFormalizacion.listarParaFormalizar`
→ `SolicitudDAO.listarAprobadasSinFormalizar` (`SolicitudDAO.java:247-258`):

```sql
SELECT ... FROM solicitudes_credito
WHERE estado_solicitud = 'APROBADA' AND NOT formalizada
ORDER BY fecha_decision
```

Orden: la aprobada más antigua primero (orden de atención). La misma vista
(`formalizacion/lista.jsp`) en realidad tiene **tres secciones**, no solo la de RF11: aprobadas
sin formalizar (esta), préstamos ya formalizados con checklist de firmados pendiente
(`listarPendientesDesembolso`, corrección de Joel) y préstamos con cierre documental pendiente
(E9/Bloque 5) — las otras dos son de RF12/RF13 y E9, no de este RF.

**Fijar la fecha del primer vencimiento (pregunta b).** Al tocar "Formalizar" sobre una fila de
la bandeja, `formalizacion/nueva.jsp` pide la fecha con un `<input type="date">` y llama a
`ServicioFormalizacion.generarDocumentos(idSolicitud, primerVencimiento, idUsuario)`
(`ServicioFormalizacion.java:210-312`), que:

1. Revalida que la solicitud siga `APROBADA` y sin préstamo ya creado (línea 218-225).
2. Revalida el tope de tasa vigente (RN-15, tercera revalidación — línea 227-240).
3. Valida el rango de la fecha propuesta (línea 242-245, ver más abajo).
4. Trae la lista de feriados activos y llama a `ServicioMotorFinanciero.cotizar(...)` (línea
   247-249), que genera el plan de cuotas corriendo cada vencimiento que caiga en fin de semana
   o feriado al siguiente día hábil (`fechaHabil`, ver abajo).
5. Crea el préstamo (`PENDIENTE_DESEMBOLSO`), el plan de pagos versión 1, marca la solicitud
   `formalizada`, audita, y genera los 3 PDF (contrato, pagaré, plan de pagos) en la misma
   transacción.

### El rango permitido

Parámetros `formalizacion.dias_min_primer_vencimiento` = 20 y
`formalizacion.dias_max_primer_vencimiento` = 45 (`bd/03_parametros.sql:181-182`) — **los
mismos 20-45 días que usa RF05 al simular/crear la solicitud**, pero contados **desde hoy** (el
día de la formalización, vía `Reloj.hoy()`), no desde la fecha de aprobación ni la de
desembolso:

```java
LocalDate hoy = Reloj.hoy();
int diasMinimo = servicioParametros.obtenerValorEntero(conexion, "formalizacion.dias_min_primer_vencimiento");
int diasMaximo = servicioParametros.obtenerValorEntero(conexion, "formalizacion.dias_max_primer_vencimiento");
validarPrimerVencimientoFormalizacion(hoy, primerVencimiento, diasMinimo, diasMaximo);
```
(`ServicioFormalizacion.java:242-245`)

Es un parámetro editable por el administrador (`ServicioParametros.obtenerValorEntero`), no un
literal en código — igual que el resto de los parámetros del sistema (`REGLAS_DESARROLLO.md`:
"nunca valores configurables en código").

### Fin de semana y feriado: se corren, no se bloquean

`ServicioMotorFinanciero.fechaHabil` (`ServicioMotorFinanciero.java:86-94`) avanza día por día
mientras la fecha sea sábado, domingo, o esté en la tabla `feriados` con `activo = TRUE`. Se
aplica dentro de `generarPlanPagos`/`cotizar` a **todas** las cuotas, incluida la primera (el
loop arranca en `k = 1` sobre la fecha teórica elegida, `ServicioMotorFinanciero.java:109-112`):
no es solo "las cuotas siguientes" — el primer vencimiento también se corre si cae en un día no
hábil. El contrato generado dice esto explícitamente: *"Cuando un vencimiento cae en sábado,
domingo o feriado, se traslada al siguiente día hábil"* (`ServicioFormalizacion.java:594-595`).

**Hallazgo — el rango no se revalida sobre la fecha ya corrida.** `validarPrimerVencimientoFormalizacion`
corre (línea 245) **antes** de `cotizar`/`fechaHabil` (línea 248-249), y lo hace sobre la fecha
**teórica** elegida por el usuario, nunca sobre la fecha real después de correrla. Si se elige
un día cercano al límite del rango (por ejemplo, el día 45) y ese día es un viernes feriado
seguido de sábado y domingo, la fecha real de la cuota 1 puede terminar más allá del día 45 sin
que nada lo detecte ni avise. El anteproyecto pide "fijar la fecha... dentro del rango permitido,
teniendo en cuenta los feriados" — hoy el sistema cumple las dos partes por separado, pero no
garantiza que el resultado combinado (fecha ya corrida) siga dentro del rango.

### Si la fecha cambia (regenerar)

`regenerarDocumentos` (`ServicioFormalizacion.java:321-369`), usado cuando todavía no hay
`CONTRATO_FIRMADO`/`PAGARE_FIRMADO` cargado (línea 329, `asegurarSinDocumentosFirmados`),
permite elegir una fecha de primer vencimiento distinta a la original. Si cambió (línea 335):
recalcula el plan completo como una nueva versión (interés, cuota, total — línea 336-350,
reutilizando monto/plazo/TEA ya aprobados, nunca otros) y regenera los 3 PDF.

**Hallazgo — el CFT no se recalcula.** `regenerarDocumentos` nunca actualiza `prestamo.cft`;
ese campo solo se fija una vez, al formalizar por primera vez, copiado de
`solicitud.getCftAprobado()` (línea 271). Cambiar la fecha del primer vencimiento cambia cuánto
tiempo corre el interés del primer período, pero el CFT impreso/guardado sigue siendo el de la
solicitud original.

**Hallazgo — el tope del 40 % de afectación no se vuelve a controlar.** La validación de
capacidad de pago (`calcularCapacidadPago`, el 40 % de RF05) solo se llama desde
`ServicioSolicitudes.java` — no hay ninguna llamada desde `ServicioFormalizacion`. Si cambiar la
fecha del primer vencimiento cambiara el monto de la cuota (por ejemplo, un primer período más
corto o más largo cambia el interés de esa cuota puntual) de forma que la afectación superara el
40 %, el sistema lo permite igual, sin aviso. En la práctica el impacto es acotado (solo la
cuota 1 cambia de monto por el interés del primer período; las demás cuotas no varían con la
fecha de inicio), pero el control no existe.

### El calendario de la pantalla no bloquea nada

`formalizacion/nueva.jsp` (línea ~64-68) usa `<input type="date" ... data-feriados='[...]' required>`
**sin `min`/`max`** — el calendario del navegador deja elegir cualquier fecha, incluida una
fuera del rango o un fin de semana/feriado. `recursos/js/fecha-primer-vencimiento.js` solo
**avisa** en el momento ("Se cobrará el dd/mm/aaaa (siguiente día hábil)") sin impedir el envío;
el control real es enteramente del servidor (`validarPrimerVencimientoFormalizacion` +
`fechaHabil`, ambos re-ejecutados dentro de la transacción, no solo en la pantalla). Mismo
mecanismo en `ficha.jsp` para el campo de "Regenerar documentos".

**Nota — esto no es un hallazgo, es un diseño correcto y deliberado**: `prestamos.primera_fecha_vencimiento`
guarda la fecha **teórica** elegida (sin corrimiento) —
`prestamo.setPrimeraFechaVencimiento(primerVencimiento)`, línea 274 — mientras que el PDF del
contrato usa la fecha **efectiva** (ya corrida) de la cuota 1 real:
`primeraFechaVencimientoEfectiva = cuotas.get(0).getFechaVencimiento()`, con comentario
explícito de Joel (`ServicioFormalizacion.java:838-842,878`). Lo impreso es correcto; el campo
crudo en `prestamos` queda como "la fecha elegida", no la fecha de cobro real — coherente,
ninguna pantalla ni reporte confunde las dos.

## 3. Auditoría

| Evento | Entidad | Cuándo | Detalle guardado |
|---|---|---|---|
| `GENERAR_DOCUMENTOS_FORMALIZACION` | PRESTAMO | Se formalizó una solicitud aprobada | "Formalizó la solicitud SOL-... como CC-...." (`ServicioFormalizacion.java:296-298`) |
| `REGENERAR_DOCUMENTOS_FORMALIZACION` | PRESTAMO | Se regeneraron los documentos (con o sin cambio de fecha) | "Regeneró los documentos de CC-....» (`ServicioFormalizacion.java:361-362`) |

**Hallazgo — la auditoría no deja constancia de la fecha del primer vencimiento.** Ni el
detalle de `GENERAR_DOCUMENTOS_FORMALIZACION` ni el de `REGENERAR_DOCUMENTOS_FORMALIZACION`
incluyen la fecha elegida, ni (en el caso de regenerar) el valor anterior. Quien audite después
puede ver *que* se formalizó o regeneró y *quién* lo hizo, pero no *qué fecha* se fijó ni si
cambió. El valor en sí queda recuperable indirectamente (está en `prestamos.primera_fecha_vencimiento`
y, tras un cambio, la versión anterior del plan sigue en `planes_pago` marcada no vigente), pero
no aparece en el registro de auditoría mismo.

## 4. Dónde está

- `servicios/ServicioFormalizacion.java` — `generarDocumentos`, `regenerarDocumentos`,
  `regenerarSoloPdfs`, `listarParaFormalizar`, `validarPrimerVencimientoFormalizacion`,
  `asegurarSinDocumentosFirmados`, `generarPdfContrato`/`generarPdfPagare`/`generarPdfPlanDePagos`.
- `servicios/ServicioFormalizacion.java` también es el **dueño del flujo guiado de este tramo**
  (01/11/2026): `calcularPasoDeLaSolicitudAprobada` (el paso que ofrecen las dos fichas de una
  solicitud aprobada, **B104** — detalle en [RF09_aprobar.md](RF09_aprobar.md) §5),
  `obtenerAvisoDeLoQueFaltaParaDesembolsar` + `avisoDeLoQueFaltaParaDesembolsar` y
  `nombrarLoQueFalta` (el aviso *"falta cargar: …"* de la tarjeta del panel, **B100** — detalle
  en [RF30_panel.md](RF30_panel.md) §2). Los cuatro son **puros** salvo el `obtener…`, que solo
  lee el checklist ya existente; ninguno agrega consultas nuevas ni toca permisos.
- `servicios/ServicioMotorFinanciero.java` — `fechaHabil` (RN-19), `cotizar`/`generarPlanPagos`
  (usa `fechaHabil` sobre cada cuota, incluida la primera).
- `servicios/ServicioCotizacion.java` — `validarRangoFechaPrimerVencimiento` (usada por RF05,
  delega al mismo `validarPrimerVencimientoFormalizacion` de `ServicioFormalizacion` — un solo
  lugar con la regla del rango, sin duplicarla).
- `dao/SolicitudDAO.java` (`listarAprobadasSinFormalizar`), `dao/PrestamoDAO.java`,
  `dao/PlanPagoDAO.java`, `dao/CuotaDAO.java`, `dao/FeriadoDAO.java` (`listarFechasActivas`).
- `controladores/ControladorFormalizacionServlet.java` — rutas `/formalizacion` (bandeja),
  `/formalizacion/{id}/generar`, `/formalizacion/{id}/regenerar`, además de "anular antes del
  desembolso" (RF14, no es de este RF).
- `webapp/WEB-INF/vistas/formalizacion/lista.jsp`, `nueva.jsp`, `ficha.jsp`.
- `recursos/js/fecha-primer-vencimiento.js` — aviso visual del corrimiento, sin bloquear.
- Tabla `feriados` (fecha, descripción, activo) — pantalla de administración en Parámetros
  (fuera del alcance de este RF, pertenece a RF-Parámetros).
- Parámetros: `formalizacion.dias_min_primer_vencimiento` = 20,
  `formalizacion.dias_max_primer_vencimiento` = 45 (`bd/03_parametros.sql:181-182`).

## 5. Dependencias

- **Depende de**: RF09/RF10 (la solicitud tiene que estar `APROBADA`); RF05 (el rango 20-45 días
  y el tope del 40 % ya se validaron una vez al crear/simular la solicitud; RF11 reusa el mismo
  rango pero no reusa el control del 40 %, ver §2); los parámetros `credito.factor_moratorio`/
  `credito.porcentaje_punitorio` (tasas de mora, se fijan al formalizar).
- **Lo afecta un cambio en**: `ServicioMotorFinanciero.fechaHabil`/`cotizar` (motor financiero
  único, cualquier cambio ahí repercute en RF05, RF11 y RF20); la tabla `feriados` (si el gerente
  agrega o quita un feriado, afecta a cualquier formalización posterior, no a los préstamos ya
  formalizados).
- **A quién afecta un cambio acá**: RF12 (PDF de contrato/pagaré/plan — ya implementado, ver
  §2/§4, ya usa la fecha efectiva correctamente); RF13 (checklist de firmados, que exige que el
  préstamo ya exista); RF14 (anular antes del desembolso); E6 (desembolso, que usa
  `prestamos.primera_fecha_vencimiento`/el plan vigente para sus cálculos).

## 6. Análisis

### 11/10/2026 — revisión completa de RF11 contra el texto firmado (pedido de Joel, solo lectura)

**Método**: lectura completa de `ServicioFormalizacion.java`, `ServicioMotorFinanciero.java`
(`fechaHabil`, `cotizar`/`generarPlanPagos`), `ServicioCotizacion.java`,
`ControladorFormalizacionServlet.java`, `formalizacion/lista.jsp`/`nueva.jsp`/`ficha.jsp`,
`FiltroAutorizacion.java`, `ServicioAutorizacion.construirMatriz`, `bd/03_parametros.sql`
(parámetros del rango y tabla de feriados), y `menu.jsp`.

**Resultado, parte del texto → cumple/no cumple/dónde:**

| Parte del texto | ¿Cumple? | Dónde |
|---|---|---|
| "mostrar las solicitudes aprobadas pendientes de formalizar" | Cumple | `listarAprobadasSinFormalizar` (`estado_solicitud = 'APROBADA' AND NOT formalizada`), bandeja `/formalizacion` |
| "permitir fijar la fecha del primer vencimiento" | Cumple | Formulario + `generarDocumentos(idSolicitud, primerVencimiento, idUsuario)` |
| "...dentro del rango permitido" | Cumple parcialmente | Se valida la fecha **elegida** contra 20-45 días desde hoy; **no se vuelve a validar después de correrla** por feriado/fin de semana — puede terminar fuera de rango sin aviso (§2) |
| "...teniendo en cuenta los feriados y los fines de semana" | Cumple | `fechaHabil` corre cualquier vencimiento (incluida la cuota 1) que caiga en sábado, domingo o feriado activo |

**Conclusión**: el texto se cumple en lo esencial — la bandeja existe con el filtro correcto, y
la fecha se puede fijar con feriados/fines de semana tenidos en cuenta. **No cumple sin
excepción**: el "dentro del rango permitido" no está garantizado después del corrimiento por día
no hábil (hallazgo de §2), y hay dos huecos adicionales, fuera del texto literal del RF pero
relevantes para la integridad del préstamo, en el cambio de fecha al regenerar (§2: CFT sin
recalcular, 40 % sin revalidar) y en la auditoría (§3: sin la fecha en el detalle).

### Controles (pregunta c)

- **Permisos**: servidor — `FiltroAutorizacion.java:53`, ruta `/formalizacion` → permiso
  `GENERAR_DOCUMENTOS_FORMALIZACION`. Matriz real (`ServicioAutorizacion.java:43,82,109`):

  | Permiso | Gerente | Oficial | Cajero |
  |---|---|---|---|
  | GENERAR_DOCUMENTOS_FORMALIZACION | TOTAL | TOTAL | NINGUNO |

  A diferencia de Solicitudes/Aprobaciones (donde oficial y gerente tienen roles distintos),
  acá los dos perfiles operan la misma pantalla con el mismo nivel de acceso — decisión ya
  tomada (D-68, comentario en `ControladorFormalizacionServlet.java:36-40`), no es un hallazgo
  nuevo. La cajera no tiene acceso (confirmado también en `ControladorDesembolsoServlet.java:143`,
  comentario sobre el 403 que recibe si intenta `/formalizacion`). ADMINISTRADOR: TOTAL en todo
  el sistema sin excepción.
- **Menú**: `menu.jsp:114,159-160` — `verFormalizacion = esOficial || esGerente || esAdministrador`,
  coherente con el permiso de servidor (la cajera no ve la entrada).
- **Solo `APROBADA`**: sí — `generarDocumentos` rechaza si el estado no es `APROBADA` (línea
  218-220) y si ya existe un préstamo para esa solicitud (línea 221-225).
- **Cliente sin OTRO préstamo abierto (RN-01, B84, 28/10/2026)**: sí —
  `validarSinOtroPrestamoAbierto`, llamado en `generarDocumentos` justo después del control de "ya
  existe un préstamo" (que es el que *prueba* que esta solicitud todavía no tiene préstamo) y
  **antes** de `generarIdentificadorOperacion` (que consume numeración) y de emitir cualquier
  documento: si rechaza, no se numeró ni se emitió nada. Reusa
  `SolicitudDAO.buscarPorClienteConPrestamoAbierto` (RN-01, D-61), **sin consulta nueva**, y
  descarta el caso de que el préstamo abierto sea el de la propia solicitud (ver §7). Mensaje:
  "El cliente ya tiene un préstamo no cerrado. No se puede formalizar. Para pedir otro crédito,
  primero debe terminar de pagarlo o cancelarlo." **No se aplica a `regenerarDocumentos`**: ahí el
  préstamo abierto es el propio, y el control bloquearía el regenerar.
- **Doble envío**: la misma revalidación de "ya existe un préstamo" (línea 221-225) es la
  protección — un segundo submit (PRG, doble clic, F5) encuentra el préstamo ya creado dentro de
  la misma revisión y lo rechaza con mensaje claro ("Esta solicitud ya fue formalizada (CC-...)."),
  no duplica nada.
- **CSRF**: cubierto globalmente por `FiltroCsrf` (patrón estándar del proyecto, sin nada
  específico de este módulo).
- **Auditoría**: ver §3 — registrado, pero sin el detalle de la fecha (hallazgo).

### Qué pasa después (pregunta d, RF12)

Ya está implementado, no es trabajo futuro: `generarPdfContrato`/`generarPdfPagare`/
`generarPdfPlanDePagos` (`ServicioFormalizacion.java:543-669`) usan JasperReports
(`.jasper` precompilados: `contrato.jasper`, `pagare.jasper`, `plan_pagos.jasper`) con el número
de operación único (`CC-aaaa-NNNNN`, vía `NumeradorDAO`), generados en la misma transacción que
crea el préstamo (línea 301-303). RF12 queda fuera del alcance de este documento (merece el
suyo si Joel quiere un recorrido dedicado), pero se confirma que no bloquea nada de RF11.

## 7. Decisiones

Decisiones del 11/10/2026 (Joel), sobre los 3 hallazgos de la sección 6 — todas implementadas:

1. **Rango después de correr la fecha**: `ServicioFormalizacion.validarPrimerVencimientoEfectivoFormalizacion`
   (nuevo, pura) valida la fecha YA corrida (`ServicioMotorFinanciero.fechaHabil`) contra el
   máximo del rango; si queda fuera, rechaza con el texto exacto pedido por Joel, sugiriendo la
   última fecha hábil válida (`ServicioMotorFinanciero.fechaHabilAnterior`, nuevo: igual que
   `fechaHabil` pero hacia atrás). Se llama en `generarDocumentos` y en `regenerarDocumentos`
   (cuando la fecha cambia), después de la validación de la fecha elegida que ya existía.
2. **CFT recalculado**: `generarDocumentos` ahora usa `resultado.getCft()` (del mismo `cotizar`
   que genera el plan) en vez de `solicitud.getCftAprobado()`; `regenerarDocumentos` lo persiste
   junto con la fecha nueva (`PrestamoDAO.actualizarPrimerVencimientoYCft`, reemplaza a
   `actualizarPrimerVencimiento`). **Aclaración importante** (para que Joel la tenga en cuenta,
   no fue parte de la pregunta pero cambia lo que "recalcular" significa en la práctica): con el
   motor actual, el CFT y la cuota más alta **no dependen de qué fecha de primer vencimiento se
   elija** — `ServicioMotorFinanciero.calcularCft`/`generarPlanPagos` descuentan por período
   secuencial (cuota 1, 2, 3...), no por la cantidad real de días entre vencimientos. Así que
   "recalcular con la fecha nueva" da, en los hechos, el mismo número que antes, siempre que
   monto/plazo/TEA no cambien (y no cambian, RN-07). Lo que la corrección sí arregla es un bug
   real: antes, `prestamo.cft` quedaba desconectado de la cotización que generó el plan (podía
   venir de una fecha estimada distinta en la solicitud) — ahora siempre es el mismo número que
   produjo el plan vigente, con una sola fuente de verdad. Confirmado con JUnit
   (`testCftYCuotaMasAltaNoCambianConLaFechaDelPrimerVencimiento`, sección 9).
3. **Tope del 40 %**: `ServicioFormalizacion.revalidarCapacidadPagoFormalizacion` (nuevo) vuelve
   a controlar la afectación con el ingreso y las otras deudas ACTUALES del cliente
   (D-127, mismo criterio que ya usa RF06 al revalidar el envío a aprobación), tanto al
   formalizar como al regenerar con una fecha nueva, sin excepción. **Mismo matiz que el punto
   2**: como la cuota no cambia con la fecha, lo único que puede hacer que este control bloquee
   algo que la aprobación no bloqueó es que la situación financiera del cliente haya empeorado
   en el tiempo transcurrido, no la fecha elegida. Por eso **el mensaje de bloqueo no sugiere
   "elegir una fecha más cercana"** (como decía el pedido original) — esa sugerencia no
   resolvería nada, ya que cualquier fecha daría la misma cuota. En su lugar, el mensaje explica
   que hay que revisar la situación del cliente o consultar al gerente
   (`construirMensajeLimiteAfectacionFormalizacion`, pura, probada con JUnit). **Esta es una
   desviación consciente del texto exacto que pidió Joel — señalada para que la confirme o la
   corrija.**
4. **Auditoría**: `construirDetalleFechaFormalizacion` (nuevo, pura) agrega al detalle de
   `GENERAR_DOCUMENTOS_FORMALIZACION`/`REGENERAR_DOCUMENTOS_FORMALIZACION` la fecha estimada de
   la solicitud (`solicitud.getPrimeraFechaVencimientoAprobada()`), la fecha elegida, la fecha
   final, el motivo del corrimiento si lo hubo (`describirCorrimiento`/`describirMotivoNoHabil`,
   con el nombre del feriado cuando corresponde), la cuota más alta y el CFT. Al regenerar sin
   cambiar la fecha, el detalle sigue siendo el simple de siempre (nada cambió para auditar).
5. **Usabilidad**: el `<input type="date">` de `nueva.jsp`/`ficha.jsp` ahora tiene `min`/`max`
   (el servidor sigue siendo el control real); `fecha-primer-vencimiento.js` reescrito para
   decir, con el nombre del día y del feriado, por qué se corre y a qué fecha ("El 07/11/2026 es
   sábado. El primer vencimiento quedará el lunes 09/11/2026."); y una tabla nueva muestra, antes
   de confirmar, la cuota más alta, el total, el CFT y la afectación actual (en rojo si supera el
   límite) — `ServicioFormalizacion.calcularResumenParaMostrar`, nuevo.

### Decisión del 28/10/2026 (Joel) — B84: revalidar RN-01 al formalizar

**La regla** ("un cliente, un préstamo a la vez") y el criterio general están en
`RF09_aprobar.md` §9. Acá queda lo propio de la formalización:

1. **`validarSinOtroPrestamoAbierto(conexion, idCliente, idSolicitud)`** (nuevo, visible en el
   paquete), llamado desde `generarDocumentos`. Reusa
   `SolicitudDAO.buscarPorClienteConPrestamoAbierto` — **sin consulta nueva**.
2. **Por qué no se bloquea a sí misma** (la pregunta de Joel al aprobar el plan): al formalizar, la
   propia solicitud **todavía no tiene préstamo** — el que se está creando nace unas líneas más
   abajo. La garantía tiene tres capas, y cualquiera de las dos primeras alcanza sola:
   - **(1) estructural**: el control corre antes del `prestamoDAO.insertar`;
   - **(2) probada dos líneas antes**: el control de "ya fue formalizada"
     (`prestamoDAO.obtenerPorSolicitud`) ya abortó si había un préstamo para esta solicitud;
   - **(3) explícita en el código**: en vez del booleano se usa la variante que **devuelve la
     solicitud dueña** del préstamo abierto, y se descarta el caso de que esa dueña sea ESTA
     solicitud (`!Long.valueOf(idSolicitud).equals(conPrestamoAbierto.getIdSolicitud())`).
     Es **redundante hoy, a propósito**: deja la garantía local y verificable por una prueba, en
     vez de depender del orden de las líneas, que un cambio futuro puede mover. Decisión de Joel
     del 28/10/2026 (eligió la capa 3 sobre la versión booleana simple).
3. **Ubicación**: antes de `generarIdentificadorOperacion` (numeración) y de emitir documentos —
   un rechazo no consume numeración ni deja ningún PDF.
4. **`regenerarDocumentos` queda fuera** a propósito (ver §6 y §10).
5. **Texto**: en usted, con el "qué sigue", idéntico al de aprobar salvo la acción — hay un JUnit
   que cruza los dos textos entre sí, así que si alguien reescribe uno solo, salta.

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 15/11/2026 | **B183 + B184 + B185** (tanda 2 de la revisión UX). **B183**: las 3 secciones de la bandeja tienen título, contador, encabezados que ordenan y **su propia paginación de 20**. Cada sección con sus parámetros (`ordenFormalizar`/`sentidoFormalizar`/`paginaFormalizar` y los de `Desembolso`/`Pagare`), porque un solo `pagina` movería las tres; las reglas viven en el servicio (`ordenAplicado`, `sentidoAplicado`, `ordenarParaFormalizar`, `ordenarPrestamosDeBandeja`, puras, con lista blanca por sección) y el orden va **en memoria**, como el filtro de texto, por el motivo de B167/B175. `plantilla/paginacion.jsp` se incluye **tres veces y con `<jsp:include>`** (con el include estático no compila) y ganó dos atributos opcionales que **no cambian nada** en las otras cuatro pantallas; `listado-vivo.js` **no se tocó**. De paso, el checklist pasó a leerse solo para las filas de la página. **19 JUnit** nuevos (`ServicioFormalizacionOrdenTest`), con el nulo al final en los dos sentidos (B177), el desempate y la clave desconocida. **B184**: la ⓘ que faltaba, en *"Generar los PDF que faltan"* — las otras dos ya la tenían desde el 21/10/2026. **B185**: la barra de acciones de la ficha queda pegada al borde de abajo (`position: sticky`, `sgcpc-tema.css` §15, mismo mecanismo que B152) con el *"Volver a la bandeja"* de siempre y, al lado, qué falta para desembolsar — del mismo dueño que la bandeja (`nombrarLoQueFalta`). `mvn test` 1017/0. **Efecto en el humo**: se rompe la línea 2178 y está medido; ver `FASE2_PLAN.md`, nota "B183 y el humo". | *(este commit)* | — |
| 14/11/2026 | **B180 + B182** (tanda 1 de la revisión UX de Formalización). **B180**: el *"Volver a la bandeja"* de *"Formalizar"* y de la ficha conserva la búsqueda — `RETORNO_BANDEJA_EN_SESION` (`"retornoBandejaFormalizacion"`), escrito **solo** por `mostrarLista` y servido **solo** por `mostrarNueva`/`mostrarFicha`, con `construirRetornoBandeja` para el único filtro de esta pantalla (`texto`); vacío deja el enlace igual que antes. Mismo mecanismo que B147/B163/B170. **B182**: el motivo del bloqueo pasó a estar **junto al botón** (*"No se va a poder generar: … El detalle está arriba, en las condiciones aprobadas."*, en rojo), con el botón **todavía habilitado** (decisión del 19/10/2026: el bloqueo real es del servidor). El dueño de la frase es `ResumenFormalizacion`, con `getMotivosDeBloqueo`/`hayBloqueoConocido`/`nombrarMotivosDeBloqueo` —puras, 5 JUnit con las cuatro cardinalidades y el borde "igual al límite no es superarlo"— y con eso las dos condiciones dejaron de estar escritas **tres** veces en `nueva.jsp`. Los dos motivos siguen siendo los únicos que la pantalla puede anticipar (§10), los mismos que rechaza `revalidarCapacidadPagoFormalizacion`. `mvn test` 997/0. Estado y detalle, en `FASE2_PLAN.md`. | *(este commit)* | — |
| 05/11/2026 | **B4** (B86, tramo transversal): los carteles de **"Generar documentos"** y **"Regenerar documentos"** resumen qué va a pasar — cliente y C.I., monto aprobado y plazo, cuota más alta, **primer vencimiento efectivo** y los 3 documentos que se emiten (en "Regenerar", el N.º de operación va en el título). Cada renglón repite la misma expresión que su fila de la tabla de la pantalla: **ningún cálculo nuevo, nada de Java**. Tres cosas que vale la pena recordar: el **número de operación no se puede nombrar al generar** (lo asigna `generarIdentificadorOperacion` dentro de la transacción, al confirmar, y consume numeración); el **primer vencimiento efectivo** depende del campo, así que la JSP deja `{fecha}` y `recursos/js/fecha-primer-vencimiento.js` escribe solo el valor en `data-confirmar-valor-fecha` (reusando el corrimiento al día hábil que ya calculaba para el aviso gris), que reemplaza `plantilla/pie.jsp`; y la última línea de "Regenerar" es **condicional a propósito**, porque `regenerarDocumentos` solo recalcula el plan y re-emite los PDF si la fecha cambió. **Probado en pantalla por Joel el mismo día** con SOL-2026-0066 (§9). **"Regenerar" quedó "no visto"**: el único préstamo pendiente de desembolso tiene los firmados cargados. `mvn test` 907/907 | *(este commit)* | — |
| 28/10/2026 | **B84**: RN-01 revalidada al formalizar (`validarSinOtroPrestamoAbierto`, reusa `buscarPorClienteConPrestamoAbierto`, con el filtro del préstamo propio). 4 JUnit nuevos (`mvn test` 804 en verde) + `PruebaRN01AprobarFormalizar` (2 pruebas con base, rollback total). Probado en pantalla por Joel el mismo día: `CC-2026-00004` formalizado sin autobloqueo (§9) | *(este commit)* | — |
| 11/10/2026 | **Segunda reprueba (Joel): D.1 confirmado OK ("21/11/2026 → lunes 23/11/2026"). D.2 (ámbar) seguía en gris en `nueva.jsp` — regresión corregida: el contenedor del aviso tiene la clase `text-muted` puesta en el JSP, que ganaba por herencia al color en línea del `<strong>`; ahora se le quita esa clase mientras se muestra el aviso de fuera de plazo.** D.3 (conservar la fecha tecleada al rechazar) sigue sin reprobarse en pantalla | *(pendiente)* | `consolidacion-rf08-rf19-lote1` |
| 11/10/2026 | Reprueba del Lote 1: **a (afectación 1 decimal), c (aviso de fuera de plazo) y d (`FechaFueraDePlazoException` como advertencia) confirmados OK en pantalla**. 3 hallazgos nuevos corregidos: "Primer vencimiento" mostraba la fecha elegida, no la efectiva (D.1, ahora "21/11/2026 → lunes 23/11/2026" cuando difieren); el aviso de "fuera de plazo" se veía en el mismo gris que el de corrimiento, ahora en ámbar oscuro (D.2); la fecha tecleada se perdía al rechazar por fuera de plazo, volvía a la propuesta por defecto, ahora se conserva (D.3, mismo mecanismo `MensajesFlash.guardarValores` de `caja/ficha.jsp`) | *(pendiente)* | — |
| 11/10/2026 | Consolidación RF08-RF19 (Lote 1): corregidos los hallazgos a, b, c, d, f, g de la sección 10 (afectación a 1 decimal, texto "desactualizada", aviso de fuera de plazo en el cliente, `FechaFueraDePlazoException` como advertencia, botón "Regenerar" fijo, `data-confirmar-boton` en "Generar"/"Regenerar") | *(pendiente)* | — |
| 11/10/2026 | Probado en pantalla completo con SOL-2026-0001 (Mirtha): rango, corrimiento por fin de semana y por feriado de prueba, formalización (CC-2026-00001), regenerar bloqueado por fuera de plazo | `7046dfa` | `rf11-probado` |
| 11/10/2026 | Implementadas las decisiones de la sección 7 (rango tras corrimiento, CFT recalculado y persistido, 40 % revalidado con datos actuales, auditoría con fechas/motivo/cuota/CFT, calendario con min/max y resumen antes de confirmar), 18 pruebas nuevas, `mvn test` 659 en verde | *(pendiente)* | — |
| 11/10/2026 | Análisis completo contra el texto firmado, sin cambios de código | *(pendiente)* | — |
| 07/11/2026 | **B12** (B86, tramo 4): **se achicó el hueco de la página 1 del contrato**, de **83 a 31 pt**. La causa no estaba en el diseño sino en una regla de JasperReports: las **bandas no admiten** `printWhenExpression` —solo los elementos—, así que el `pageHeader` reservaba sus 80 pt en la página 1 aunque su contenido estuviera oculto ahí. El membrete repetido de las páginas 2+ pasó de **5 líneas a una** (logo chico, razón social y *"CONTRATO N.º …"*, con una línea debajo): la página 1 ya trae el membrete completo —RUC, domicilio, teléfono y Registro BCP— en la banda `title`, así que en las siguientes alcanza con decir de qué documento se trata. **Solo presentación**: ninguna cláusula, ningún dato y ningún cálculo cambió. Joel eligió esta opción entre dos, las dos **medidas renderizando** (la otra, un subreporte común, dejaba el hueco en 3 pt y quedó registrada como **B119** porque alcanza también a `plan_pagos` y `pagare`). El ancho del número se midió contra el identificador real (formato fijo `CC-AAAA-NNNNN`). Los contratos ya emitidos no cambian (D-94). Probado con el PDF real por Joel el mismo día (§9) | `187d0a7` | — |
| 07/11/2026 | **B120** (B86; extensión de la opción (b) de B12, pedida por Joel): el **plan de pagos** recibe el mismo membrete de continuación que el contrato. Hueco de la página 1: **85 → 33 pt**. El ancho del número se midió **de nuevo**, no se copió: *"PLAN DE PAGOS N.º "* más el identificador son 31 caracteres, cinco más que en el contrato, así que el campo va a 200 pt. **Y un segundo arreglo, preexistente**, que vio Joel en la muestra: en la página 1 el domicilio se partía en dos líneas. No lo causó este cambio (la banda `title` quedó idéntica a HEAD, verificado) — el campo mide 280 pt **igual que en el contrato** pero estaba en **fuente 9** contra la **8** del contrato, y no se podía ensanchar porque la placa azul arranca justo al lado. Pasó a 8 y entra en una línea, verificado contra el domicilio **real** de `parametros_sistema` (64 caracteres). **El pagaré no entra**: su `pageHeader` es el membrete de la página 1, no una franja repetida, y sale en una sola página. **Solo presentación**: ninguna cuota, ningún total y ningún cálculo cambió. Los planes ya emitidos no cambian (D-94). Probado con el PDF real por Joel el mismo día (§9) | `ebe6af1` | — |

## 9. Pruebas realizadas en pantalla

**05/11/2026 (Joel) — B4 (la confirmación resume qué va a pasar):**
- **`/formalizacion/nueva/66`** (`SOL-2026-0066`, Justiniano Sanabria, perfil oficial): el cartel
  de *"Generar documentos"* mostró, **en renglones**, cliente y C.I., *"Monto aprobado: 2.000.000
  Gs. a 6 meses"*, *"Cuota más alta: 361.218 Gs."*, *"Primer vencimiento: lunes 07/12/2026"* y la
  línea de los 3 documentos, con el foco en **"No, volver"**. La fecha propuesta era **05/12, un
  sábado**: el cartel mostró el **día hábil real**, no la tecleada. **OK.**
- **Segunda apertura con otra fecha** (cancelar → poner 10/12 → reabrir): el cartel dijo *"jueves
  10/12/2026"*. Esa segunda vez es la que importa — es la que habría fallado si el valor se
  leyera con `.data()` de jQuery, que cachea el atributo en la primera lectura.
- **Nunca se confirmó**: al terminar, `SOL-2026-0066` seguía en la bandeja sin formalizar.
- **"Regenerar documentos": NO VISTO.** El único préstamo pendiente de desembolso
  (`CC-2026-00006`) tiene el checklist de firmados completo y la pantalla dice *"No se puede
  regenerar: ya hay documentos firmados cargados"*. Haría falta **un préstamo pendiente de
  desembolso sin ningún firmado cargado**; armarlo solo para la foto es lo que la Lista de
  control B86 prohíbe. El texto está escrito y la JSP compila, pero eso **no es** una prueba
  (DOGMA 1).

**28/10/2026 (Joel), después del Clean and Build — B84 (RN-01 al formalizar):**
- **`SOL-2026-0004` formalizada** (Joel Acosta, 1.000.000 a 6 meses): **`CC-2026-00004`** creado,
  `PENDIENTE_DESEMBOLSO`, formalizado 28/10/2026 10:37, con contrato, pagaré y plan generados.
  Primer vencimiento elegido 28/11 (sábado) → corrido al lunes **30/11/2026**, como manda el
  corrimiento de §2. **El control nuevo no se autobloqueó** con el préstamo que estaba creando —
  es la verificación en pantalla de la capa 3 de §7, punto 2. **OK.**
- **Con `CC-2026-00004` ya abierto** (`PENDIENTE_DESEMBOLSO` cuenta como abierto), una solicitud
  nueva para el mismo cliente quedó bloqueada con el mensaje de RN-01 y botón "Entendido" (ver
  `RF09_aprobar.md` §11). La regla quedó verificada de punta a punta.
- El rechazo **al formalizar** no es alcanzable por pantalla (`ServicioSolicitudes.crear` bloquea
  antes): lo cubre `PruebaRN01AprobarFormalizar`, que lo prueba con los datos reales de la base —
  la prueba eligió sola `CC-2026-00001` (cliente 1) y `SOL-2026-0004` como "la que se formaliza",
  o sea el mismo escenario de esta prueba en pantalla, y verifica además que con el préstamo
  abierto siendo el de la propia solicitud **no** bloquea.

**11/10/2026 (Joel), después del Clean and Build, con SOL-2026-0001 (Mirtha):**
- Bandeja `/formalizacion` con SOL-0001 en la lista de aprobadas pendientes de formalizar.
- Rango mostrado: "Entre 31/10/2026 y 25/11/2026" (20-45 días desde hoy, 11/10/2026) — coincide
  exacto con lo calculado en el análisis (sección 6/10). Fecha propuesta: 11/11/2026 (un mes
  desde hoy).
- Corrimiento por fin de semana: 07/11/2026 (sábado) → aviso "El 07/11/2026 es sábado. El
  primer vencimiento quedará el lunes 09/11/2026." — correcto, con el nombre del día.
- Corrimiento por feriado: se cargó un feriado de prueba el 25/11/2026 ("Feriado de prueba
  RF11") desde Parámetros → Feriados → aviso "El 25/11/2026 es feriado (Feriado de prueba
  RF11). El primer vencimiento quedará el jueves 26/11/2026." — correcto, con el nombre del
  feriado real (no solo "feriado" genérico).
- Formalización con 24/11/2026 (hábil, dentro del rango): generó CC-2026-00001, préstamo
  `PENDIENTE_DESEMBOLSO`. El plan corrió las cuotas que cayeron en fin de semana (25/01, 26/04,
  26/07, 25/10/2027) — confirma que el corrimiento se aplica a TODAS las cuotas, no solo a la
  primera.
- **Regenerar con 25/11/2026** (el día del feriado de prueba, exactamente el máximo del rango):
  **BLOQUEADO** con el texto exacto de la sección 7 punto 1 ("La fecha elegida cae en un día no
  hábil y, al pasarla al siguiente día hábil, queda fuera del plazo permitido..."), sin cambiar
  el plan — confirma el hallazgo 1 cerrado.
- El feriado de prueba se desactivó después de la prueba (Parámetros → Feriados →
  "Desactivar"), sin necesidad de borrarlo — confirma la respuesta a la pregunta f de la vuelta
  anterior.

**Resultado: RF11 queda probado completo.** 8 mejoras menores encontradas (sección 10, a-h), NO
corregidas en esta vuelta.

**No cubierto por JUnit** (necesita conexión real, ya documentado): la revalidación del 40 %
con los datos ACTUALES de una persona concreta, y la persistencia del CFT/fecha al regenerar —
esta prueba en pantalla es la que de hecho ejerce ese camino (CC-2026-00001 formalizado, con su
CFT persistido desde el `cotizar` real).

**14/10/2026, Tanda 2, probado en pantalla por Joel** — tag `mejoras-tanda2-probado`:
`/formalizacion/1` muestra "Plan vigente después del adelanto de capital del 12/10/2026."
debajo de "Plan de pagos", para el plan vigente regenerado por RF27. **OK.**

## 10. Pendientes y trabajo futuro

- **(28/10/2026, B84, anotado sin resolver — pedido de Joel)**: el **40 % de afectación (RF05) no
  suma la cuota de un préstamo propio del cliente**. `ServicioMotorFinanciero.sumarCuotasOtrasDeudas`
  devuelve tal cual el campo `personas_laborales.cuota_otras_deudas_mensual` (lo que el oficial
  carga a mano), y ningún código consulta los préstamos de la casa para sumarlos a la afectación —
  ni en la cotización, ni al enviar, ni en la revalidación de §7 punto 3 de este documento.
  **Hoy no produce ningún error de cálculo**, justamente porque RN-01 impide que un cliente tenga
  dos préstamos a la vez. Queda atado a B84: **si alguna vez se relajara la regla de "un préstamo
  a la vez", habría que sumar explícitamente la cuota del préstamo propio**, o el 40 % quedaría
  mal calculado. Sin acción ahora.
- **(28/10/2026, B84)**: `regenerarDocumentos` **no** lleva el control de RN-01 y no debe
  llevarlo (el préstamo abierto es el propio; el control bloquearía el regenerar). Anotado para
  que nadie lo "complete" por simetría — está dicho también en el Javadoc del método.
- **(20/10/2026, tanda de usabilidad, punto 6 paso 4)**: la bandeja (`formalizacion/lista.jsp`)
  ya tiene buscador con AJAX (`listado-vivo.js`), por N.º de solicitud/operación, cédula o nombre
  del cliente -- UN solo campo filtra las 3 secciones a la vez (para formalizar, pendientes de
  desembolso, cierre documental pendiente), reutilizando `ServicioSolicitudes.filtrarPorTexto`
  para la primera y un nuevo `ServicioFormalizacion.filtrarPrestamosPorTexto` para las otras dos.
- ~~**(18/10/2026, hallazgo de Joel, prueba de CC-2026-00001)**: cuando el checklist de
  formalización dice "Documentación completa — listo para desembolso", la pantalla no dice qué
  sigue. Debería indicar "Siguiente paso: la cajera realiza el desembolso desde Desembolso y
  caja → Desembolsos" (REGLAS_DESARROLLO.md §12.4, "todo proceso termina diciendo qué sigue").
  Anotar, no implementar — queda para la tanda de usabilidad (ver `AVANCE.md`).~~ — **resuelto
  (19/10/2026, tanda de usabilidad, Commit 1 de 3)**: `formalizacion/ficha.jsp` ahora dice
  "Documentación completa: listo para desembolso. Siguiente paso: la cajera lo desembolsa desde
  Desembolso y caja → Desembolsos." (nombre real del menú, `plantilla/menu.jsp`).
- ~~**(18/10/2026, hallazgo de Joel, prueba de CC-2026-00001, ver `AVANCE.md`)**: el bloqueo por
  40 % se avisa en rojo ANTES de generar los documentos (`resumenFormalizacion.isSuperaLimite()`),
  pero el bloqueo por falta de comprobante de ingreso recién aparece al presionar "Generar
  documentos"/"Regenerar" — debería avisarse de entrada, igual que el del 40 %.~~ — **resuelto
  (19/10/2026, tanda de usabilidad, Commit 2 de 3)**: `ServicioFormalizacion.ResumenFormalizacion`
  agrega `mensajeSinComprobante` (reutiliza `construirMensajeSinComprobanteFormalizacion`, el
  mismo mensaje que ya usa la revalidación real), calculado en `calcularResumenParaMostrar`.
  `formalizacion/nueva.jsp` y el bloque "Regenerar documentos" de `formalizacion/ficha.jsp`
  muestran la fila "Comprobante de ingreso" en rojo desde el principio, mismo criterio que
  "Afectación actual" — sin deshabilitar "Generar documentos"/"Regenerar" (igual que el aviso del
  40 %, el bloqueo real sigue siendo el del servidor al confirmar).
- ~~**(13/10/2026, Joel, Tanda 2 — anotar, no implementar)**: en `/formalizacion/{id}`, cuando el
  plan de pagos vigente ya NO es el original (`planes_pago.origen_plan = 'ANTICIPO_PARCIAL'`, un
  adelanto de capital de RF27 lo regeneró), la pantalla no lo avisa — "Condiciones" sigue
  mostrando el plazo/monto aprobado originales (correcto, es la foto de la aprobación) pero la
  tabla del plan de pagos de abajo ya tiene menos cuotas y otro capital, sin ninguna nota que lo
  explique. Confirmado con CC-2026-00001 (ver análisis de la sesión del 13/10/2026 sobre este
  mismo documento): el plan vigente viene de un adelanto de capital del 12/10/2026. Propuesta: un
  aviso tipo "Plan vigente después del adelanto de capital del dd/mm/aaaa" (con la fecha de
  `planes_pago.fecha_generacion` del plan vigente) cuando `origen_plan != 'ORIGINAL'`, mismo
  criterio que ya usa `estado_cuenta_ficha.jsp` para `PENDIENTE_DESEMBOLSO`/
  `ANULADO_ANTES_DESEMBOLSO`.~~ — **resuelto y probado en pantalla el 14/10/2026** (ver §9).

**Reprueba del Lote 1 (11/10/2026, Joel): a, c y d confirmados OK en pantalla.** b, f y g
quedan pendientes de la próxima reprueba (no se repitieron en esta vuelta). 3 hallazgos nuevos
de esta misma reprueba: **D.1 confirmado OK en pantalla** (fecha efectiva vs. elegida). **D.2
(color del aviso de fuera de plazo) tuvo una regresión** (seguía en gris en `nueva.jsp`),
corregida de nuevo el 11/10/2026 — pendiente de reprueba puntual de esta segunda corrección.
**D.3 (fecha conservada al rechazar) todavía no se reprobó en pantalla.**

Mejoras menores encontradas en la prueba de pantalla del 11/10/2026 — **a-d, f y g implementados
el 11/10/2026 en la consolidación RF08-RF19 (Lote 1, items A.6, A.7, A.8 y B.12/B.11)**:

a) ~~La afectación se muestra con todos los decimales ("17,40442222 %")~~ — ahora usa
   `FormateadorNumeros.comoPorcentajeUnDecimal` en `nueva.jsp` y `ficha.jsp` (1 decimal, "17,4 %").
b) ~~Texto "puede haber quedado vieja"~~ → ahora dice "desactualizada" (`nueva.jsp`).
c) ~~El aviso de la pantalla no avisaba si el corrimiento dejaba la fecha fuera de plazo~~ —
   `fecha-primer-vencimiento.js` ahora compara contra el `max` del campo y agrega el aviso
   adicional antes de confirmar (el servidor sigue siendo el bloqueo real).
d) ~~El bloqueo por fuera de plazo salía como error (X roja)~~ — `validarPrimerVencimientoEfectivoFormalizacion`
   ahora lanza `FechaFueraDePlazoException` (subtipo de `NegocioException`), capturada en
   `ControladorFormalizacionServlet` (generar y regenerar) y mostrada como advertencia ("Entendido").
   La revisión de `SolicitudYaDecididaException` (RF09/RF10) sigue pendiente de verificar en
   pantalla — ver [RF09_aprobar.md](RF09_aprobar.md) §12.
e) Después de agregar un feriado desde Parámetros, la pantalla vuelve a la pestaña general en
   vez de quedarse en la pestaña Feriados — **corregido el 11/10/2026** (B.13, consolidación
   RF08-RF19) en `ControladorParametrosServlet`/`parametros/administracion.jsp`, fuera del
   alcance de este documento (pertenece a la pantalla de Parámetros) — pendiente de reprueba ahí.
f) ~~En "Regenerar documentos" (ficha.jsp), el botón "Regenerar" se corría a la derecha~~ —
   reordenado: el botón queda fijo junto a la fecha, el aviso/rango pasan a su propia línea.
g) ~~Confirmaciones con "Sí" genérico~~ — ahora "Sí, generar" (`nueva.jsp`) y "Sí, regenerar"
   (`ficha.jsp`), y se revisaron y corrigieron otras 11 pantallas del sistema con el mismo
   problema (ver la tabla del plan de consolidación, punto B.11).
h) ~~En el guion de defensa (sección 11, punto 4), agregar la explicación de que el interés se
   calcula por período mensual secuencial~~ — **resuelto (13/10/2026)**: el punto 4 de la
   sección 11 ya trae la explicación en términos simples ("el interés se calcula por período
   mensual secuencial..., no por la cantidad real de días entre vencimientos... Por eso la
   cuota, el total y el CFT no cambian si el primer vencimiento se elige a 20 días o a 45"),
   citando la aclaración técnica de la sección 7 punto 2. Solo documentación, sin cambio de
   código.

## 11. Guion para la defensa

**Confirmado en pantalla el 11/10/2026 (sección 9), salvo el punto 8 (señalado abajo).**

1. Mostrar la bandeja `/formalizacion` con SOL-2026-0001 (Mirtha, aprobada hoy 11/10/2026 10:57)
   en la lista de aprobadas pendientes de formalizar.
2. Elegir una fecha de primer vencimiento que caiga en sábado o domingo (propuesta: **07/11/2026**,
   sábado, día 27 dentro del rango 20-45) — mostrar el aviso en pantalla ("El 07/11/2026 es
   sábado. El primer vencimiento quedará el lunes 09/11/2026.") antes de confirmar, y que el plan
   generado efectivamente usa el lunes.
3. Mostrar el calendario con `min`/`max`: el navegador ya no deja abrir fechas fuera del rango.
   Intentar forzar el campo igual (fuera del rango, o editando el HTML) — mostrar el rechazo del
   servidor, no solo de la pantalla.
4. Mostrar la tabla de resumen antes de confirmar (cuota más alta, total, CFT, afectación
   actual) — confirmar que son los mismos valores que la ficha de aprobación ya mostraba, y
   explicar por qué: **el interés se calcula por período mensual secuencial (cuota 1, cuota 2,
   ...), no por la cantidad real de días entre vencimientos** — el mismo criterio que usa la
   cotización de RF05. Por eso la cuota, el total y el CFT no cambian si el primer vencimiento
   se elige a 20 días o a 45 (sección 7, punto 2) — lo único que cambia con la fecha es el
   calendario de cobro, nunca los montos.
5. Mostrar los 3 PDF generados (contrato, pagaré, plan de pagos) con la fecha efectiva correcta
   en el contrato, y la auditoría con el detalle nuevo (fecha estimada/elegida/final, motivo del
   corrimiento, cuota más alta, CFT).
6. Entrar como oficial y como gerente, mostrar que los dos pueden formalizar; entrar como cajera
   (forzando la URL) y mostrar el 403.
7. Intentar formalizar la misma solicitud dos veces (doble clic o F5) — mostrar que no duplica,
   con el mensaje "Esta solicitud ya fue formalizada (CC-...)."
8. Con un feriado de prueba cargado desde Parámetros → Feriados (se usó "25/11/2026, Feriado de
   prueba RF11" en la prueba de pantalla), elegir esa fecha — mostrar el aviso con el nombre del
   feriado real, no solo "sábado"/"domingo" ("El 25/11/2026 es feriado (Feriado de prueba
   RF11). El primer vencimiento quedará el jueves 26/11/2026."). **Confirmado en pantalla.**
9. Regenerar un préstamo con una fecha que, al corrérse, queda fuera del rango (se usó
   CC-2026-00001 con 25/11/2026, el feriado de prueba del punto 8, exactamente el máximo del
   rango) — mostrar el bloqueo con el mensaje exacto ("La fecha elegida cae en un día no hábil
   y, al pasarla al siguiente día hábil, queda fuera del plazo permitido..."), sin que el plan
   cambie. **Confirmado en pantalla** (mejora pendiente: hoy sale como error, debe ser
   advertencia — sección 10, punto d).
10. **RN-01 al formalizar (B84, 28/10/2026)**: formalizar una solicitud de un cliente **sin** otro
    préstamo abierto — mostrar que sale normal (se usó `SOL-2026-0004` → **`CC-2026-00004`**,
    primer vencimiento 28/11 sábado → lunes 30/11/2026). Explicar que, aunque al formalizar se
    está creando un préstamo, el control **no se bloquea a sí mismo**: mira los OTROS préstamos
    del cliente (§7, punto 2). Y después, con ese préstamo ya abierto, mostrar que una solicitud
    nueva para el mismo cliente queda bloqueada. **Confirmado en pantalla.**

    > **Nota para la defensa (28/10/2026)**: "un cliente, un préstamo a la vez" es **política de
    > crédito del dueño**, no un requisito del anteproyecto — ningún RF la exige. Vale como
    > decisión de Joel, nivel 2 de la jerarquía de requisitos (`CLAUDE.md`, 18/10/2026). Mismo
    > texto de nota que `RF09_aprobar.md` §13, punto 8.
