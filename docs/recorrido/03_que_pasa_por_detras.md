# Anexo — Qué pasa por detrás (Escena 03, tramo 2, rol gerente)

Mismo formato que [01-02_que_pasa_por_detras.md](01-02_que_pasa_por_detras.md): complementa
[03_aprobacion.md](03_aprobacion.md) con lo que pasa en el servidor y en la base, verificado
leyendo el código real (solo lectura, sin cambiar nada). Tramo: login del gerente → panel →
bandeja → revisión de `SOL-2026-0001` → **Aprobar (ya ejecutado hoy)**; Rechazar/Cancelar/Volver
se describen en el punto 4, sin ejecutarlos. Lo que no pude confirmar con una lectura directa
queda marcado **"a confirmar"**.

## 0. Login del gerente (RF36)

Mismo mecanismo que el login del oficial (ver
[01-02_que_pasa_por_detras.md](01-02_que_pasa_por_detras.md) §0): lee/escribe `usuarios`,
audita `ACCESO_CONCEDIDO`. El sistema no distingue "sesiones simultáneas" de ningún modo
especial — cada usuario tiene su propia `HttpSession`; no hay ningún límite de sesiones
concurrentes ni bloqueo entre usuarios distintos (el único bloqueo optimista real,
`marcarDecidida`, es sobre una FILA, no sobre sesiones — ver el punto 4 más abajo).

## 1. Panel (RF30)

- **Lee**: según el perfil (todos los indicadores de abajo están en `ServicioPanel.
  calcularIndicadores`, un único método que arma distinto según `perfil`):
  - Los 3 perfiles: `pagos` (cobros de hoy — `contarConfirmadosEntreFechas`/
    `sumarAplicadoEntreFechas`), `prestamos` (en mora — `listarEnMora`), `numeradores` +
    `parametros_sistema` (`fiscal.timbrado_vigencia_desde`/`hasta`,
    `fiscal.factura_numero_hasta`, `fiscal.factura_numero_aviso` — aviso de timbrado agotándose).
  - Oficial + gerente + administrador: `gestiones_cobranza` (promesas de hoy).
  - Cajero + gerente + administrador: `prestamos` (pendientes de desembolso, listos para
    desembolsar — este último también mira el checklist de `documentos_expediente`),
    `sesiones_caja`/`movimientos_caja` (fondo sugerido, si hay caja abierta, saldo teórico).
  - **Gerente + administrador** (el bloque que importa para este tramo): `cuotas` (capital
    pendiente de los préstamos activos — "cartera en la calle"), `pagos` (cobrado en la semana/
    mes), **`solicitudes_credito`** (`listarPendientesAprobacion` — el indicador "1 solicitudes
    pendientes de aprobar"), `reversiones_pago` (`listarSolicitadas` — reversiones por
    autorizar).
  - Solo oficial: sus propias solicitudes en curso (no aplica a este tramo).
- **Escribe**: condicional — **la PRIMERA vez que se abre `/panel` (o `/mora`, o un informe de
  Morosidad/Cartera/Incobrables) en el día, para cualquier usuario**, dispara
  `ServicioMora.recalcularMorosidadDiaria`: recorre `prestamos` ACTIVO/EN_MORA y, si el estado
  de alguno cambia, `UPDATE prestamos.estado_prestamo` + `auditoria_operaciones`
  (`RECALCULAR_ESTADO_MORA`, uno por préstamo cambiado). Al final siempre escribe una fila
  marcadora `RECALCULO_MORA_DIARIO` (tipo_entidad `SISTEMA`), que es la que el método usa para
  saber que "ya corrió hoy" y no repetirlo — **a confirmar**: si el oficial, en el tramo 1, ya
  abrió el panel o algún informe de mora antes que el gerente, esta escritura ya habrá ocurrido
  y el panel del gerente solo *lee*; si el gerente es el primero en abrir una de esas pantallas
  hoy, el panel SÍ escribe en ese momento. No determiné cuál de los dos pasó en la prueba de
  hoy sin consultar la base.
- **Permiso**: `/panel` **no tiene ninguna entrada en `FiltroAutorizacion`** — está abierto a
  cualquier usuario autenticado, de cualquier perfil; lo que cambia es el contenido que
  `ServicioPanel` arma según `perfil`, no el acceso a la ruta.
- **Auditoría propia de "ver el panel"**: ninguna — solo la del recálculo de mora, condicional,
  descrita arriba.
- **Archivos / numeración**: ninguno.

## 2. Bandeja de aprobación (RF07)

- **Lee**: `solicitudes_credito` (`listarPendientesAprobacion`, o `listarDecididasConFiltro` en
  la pestaña Historial), `personas` (una consulta por cliente distinto, para la columna
  Cliente), `usuarios` (en Historial, para "Decidida por").
- **Escribe**: nada.
- **Cómo calcula "Esperando"**: `Duration.between(fechaEnvioAprobacion, Reloj.ahora())` — la
  marca de tiempo es `solicitudes_credito.fecha_envio_aprobacion` (se guarda en el mismo paso
  que pasa a `PENDIENTE_APROBACION`, Escena 02 Paso 2); no existe ninguna marca de "el cliente
  llegó" — confirma lo que ya se observó en pantalla: es correcto que cuente desde el envío.
- **Cómo filtra el buscador**: texto libre, en memoria (`ServicioAprobacionCredito.
  filtrarPorTexto`, sin SQL `WHERE`) — compara código (contiene), cédula normalizada
  (con o sin puntos) y nombre normalizado (sin acentos, en los dos órdenes
  nombre+apellido/apellido+nombre), igual mecanismo que Clientes/Mora. Se aplica DESPUÉS del
  filtro de estado/fecha de Historial (nunca lo reemplaza, los dos valen juntos).
- **Permiso**: `/aprobaciones` exige `APROBAR_RECHAZAR_SOLICITUD` — solo GERENTE_ADMINISTRADOR
  (y ADMINISTRADOR, que ve todo) tiene `TOTAL`; oficial y cajero no tienen acceso a esta ruta.
- **Auditoría**: ninguna por entrar a la bandeja.
- **Archivos / numeración**: ninguno.

## 3. Revisión de la solicitud (RF08)

- **Lee**: `solicitudes_credito`, `personas`, `personas_laborales`, `personas_domicilio` +
  `ciudades`/`departamentos` (zona), `consultas_crediticias` (resultado del buró, la vinculada
  al enviar MÁS la más reciente con evidencia, combinadas sin duplicar),
  `parametros_sistema` (`credito.edad_minima`/`maxima`, `credito.departamentos_habilitados`,
  `credito.situaciones_laborales_admitidas`), `documentos_expediente` (cédula, comprobante de
  ingreso), `limites_tasa_usura` (tope vigente, para el glosario de TEA), `feriados` (para
  recalcular el plan), `motivos_rechazo_solicitud` (lista para el `<select>` de Rechazar).
- **Escribe**: **nada** — `mostrarFicha` (`ControladorAprobacionesServlet`) es de solo lectura,
  sin ningún `servicioAuditoria.registrar` ni `UPDATE`/`INSERT` en el camino. Abrir la revisión
  o mirar la evidencia del buró **no deja nada nuevo en `auditoria_operaciones`** — mismo
  criterio que el resto del sistema: ver una pantalla sin exportar/decidir nada no se audita
  (ya documentado como pendiente general en `RF40_auditoria.md` §10, no es un hallazgo nuevo de
  este tramo).
- **Qué se RECALCULA en vivo vs. qué queda CONGELADO**:
  - **El checklist de 8 requisitos se recalcula en vivo**, cada vez que se abre la ficha
    (`ServicioSolicitudes.construirChecklistPrevioEnvio`, el mismo método que ya usa el oficial
    antes de enviar) — contra la política y los datos ACTUALES del cliente (edad recalculada
    con `Reloj.hoy()`, departamento actual, situación laboral actual, documentos vigentes
    actuales, consulta al buró más reciente). Solo se muestra mientras la solicitud sigue
    `PENDIENTE_APROBACION` (en una ya decidida no se calcula, para no confundir una política que
    cambió después con la decisión real que se tomó en su momento).
  - **Las condiciones solicitadas (monto, plazo, TEA/TNA/CFT, afectación 9,3 %) son el
    SNAPSHOT congelado al enviar** (columnas `monto_solicitado`/`plazo_solicitado_meses`/
    `tasa_tea_simulada`/`porcentaje_afectacion` de `solicitudes_credito`, leídas tal cual, sin
    recalcular) — coherente con que la pantalla las muestre "de solo lectura".
  - **El plan de cuotas/total a pagar exacto SÍ se recalcula** (`ServicioSolicitudes.
    recalcularPlan`, puro, usa el mismo monto/plazo/fecha/TEA ya congelados — no vuelve a leer
    ningún parámetro de política) — solo para mostrar el total exacto (suma del plan), no para
    cambiar ninguna condición.
  - **El resultado del buró mezcla ambos**: la consulta vinculada al enviar (trazabilidad del
    envío) MÁS la más reciente con evidencia del cliente, si es distinta (para no perder una
    consulta registrada después del envío) — ninguna de las dos se recalcula, se leen tal cual
    están guardadas.
- **Permiso**: mismo permiso que la bandeja (`APROBAR_RECHAZAR_SOLICITUD`, por ruta `/aprobaciones`).
- **Archivos / numeración**: ninguno.

## 4. Los botones

Aprobar ya se ejecutó hoy (ver el detalle real abajo); Rechazar/Cancelar/Volver se describen —
qué harían, con qué condición, qué permiso exigen — **sin ejecutarlos**.

### Aprobar (RF09) — EJECUTADO hoy sobre `SOL-2026-0001`

- **Cuándo aparece/se bloquea**: siempre visible mientras la solicitud está
  `PENDIENTE_APROBACION`; al tocarlo, el servidor (`ServicioAprobacionCredito.aprobar`) revalida
  TODO de nuevo antes de guardar nada: la solicitud sigue pendiente (si alguien más ya decidió
  en otra pestaña, avisa quién y cuándo, sin aprobar); el tope de tasa (`limites_tasa_usura`)
  sigue vigente para la TEA simulada (si cambió, pide volver a simular, como advertencia, no
  error); la afectación congelada sigue dentro del límite congelado; y **sin mora en Informconf
  (RN-18)** — revincula primero la consulta más reciente con evidencia del cliente (si hay una
  nueva desde el envío) y, si esa consulta indica mora, bloquea sin aprobar nada, con el mensaje
  de que hay que rechazar.
- **Permiso**: `APROBAR_RECHAZAR_SOLICITUD` (gerente/administrador).
- **Qué pidió al usuario**: solo la confirmación en pantalla (no pide ningún dato adicional —
  las condiciones ya están fijadas desde el envío, RN-07: nunca se aprueba otro monto/plazo).
- **Qué escribió** (`UPDATE solicitudes_credito`): `estado_solicitud`:
  **`PENDIENTE_APROBACION` → `APROBADA`**, más `monto_aprobado`/`plazo_aprobado_meses`/
  `tasa_tna_aprobada`/`tasa_tea_aprobada`/`cft_aprobado`/`primera_fecha_vencimiento_aprobada`
  (copiados de los "solicitados", nunca otro valor), `id_limite_tasa_aplicado`, `decidido_por`;
  `UPDATE consultas_crediticias` (vincular la consulta revisada, si era nueva). Auditoría:
  `APROBAR_SOLICITUD` ("Aprobar solicitud"), entidad SOLICITUD_CREDITO, con el detalle de qué
  consulta(s) vio el gerente al decidir.
- **Lo que se vio en pantalla, coherente con lo anterior**: franja verde "Aprobada por
  `<gerente>` el `<fecha y hora>`. Monto aprobado: 3.000.000 Gs., plazo: 12 meses." — los 3
  datos (quién, cuándo, monto/plazo) vienen exactamente de las columnas recién escritas
  (`decidido_por`, `fecha_decision`, `monto_aprobado`, `plazo_aprobado_meses`).

### Rechazar (RF10) — sin ejecutar

- **Cuándo aparece/se bloquea**: siempre visible mientras está `PENDIENTE_APROBACION`; exige
  elegir un motivo de la lista (`motivos_rechazo_solicitud`) y, solo si el motivo elegido lo
  requiere (hoy, "Otro"), un detalle en texto libre.
- **Permiso**: `APROBAR_RECHAZAR_SOLICITUD`.
- **Qué pide al usuario**: el motivo (obligatorio) y el detalle (obligatorio solo para "Otro").
- **Si se ejecutara**: `UPDATE solicitudes_credito` — `estado_solicitud`:
  **`PENDIENTE_APROBACION` → `RECHAZADA`**, `decidido_por`, `id_motivo_rechazo`,
  `motivo_rechazo` (texto combinado); `UPDATE consultas_crediticias` (misma revinculación que
  Aprobar). Auditoría: `RECHAZAR_SOLICITUD` ("Rechazar solicitud"), entidad SOLICITUD_CREDITO,
  con el motivo y las consultas vistas en el detalle. Queda `RECHAZADA` para siempre (no se
  puede volver a decidir esa misma solicitud).

### Cancelar solicitud (RF06, camino del gerente) — sin ejecutar

- **Cuándo aparece/se bloquea**: visible mientras la solicitud está `PENDIENTE_APROBACION` o
  `APROBADA` (todavía sin formalizar) — es el "segundo camino de cancelación" que no pasa por
  `ServicioSolicitudes.cancelar` (ese es solo para `BORRADOR`, del oficial), sino por
  `ServicioAprobacionCredito.cancelar`. Exige un motivo de la misma lista fija que usa el
  oficial, con detalle obligatorio solo en "Otro".
- **Permiso**: `APROBAR_RECHAZAR_SOLICITUD` (es una acción de esta misma pantalla, no de
  Solicitudes).
- **Qué pide al usuario**: el motivo (lista fija) y el detalle si corresponde.
- **Si se ejecutara**: `UPDATE solicitudes_credito` — `estado_solicitud` → **`CANCELADA`**,
  `cancelado_por`, `motivo_cancelacion`. Auditoría: `CANCELAR_SOLICITUD` ("Cancelar solicitud"),
  entidad SOLICITUD_CREDITO, con el motivo. Mismo evento y entidad que la cancelación del
  oficial — se distinguen por `id_usuario` y por el texto del detalle.

### ← Volver a la bandeja — sin ejecutar

- **Cuándo aparece/se bloquea**: siempre visible, sin condición.
- **Permiso**: ninguno adicional — es un enlace a `/aprobaciones`, ya dentro de la misma pantalla
  protegida.
- **Qué pide al usuario**: nada.
- **Si se ejecutara**: nada — es solo un `GET` de navegación, no escribe ni audita nada.

## Matriz de permisos relevante para este tramo

| Permiso | Ruta/acción | Gerente | Oficial | Cajero |
|---|---|---|---|---|
| `APROBAR_RECHAZAR_SOLICITUD` | `/aprobaciones` (bandeja, revisión, Aprobar/Rechazar/Cancelar) | TOTAL | NINGUNO | NINGUNO |
| `GENERAR_DOCUMENTOS_FORMALIZACION` | `/formalizacion` | **TOTAL** | TOTAL | NINGUNO |

**Corrección sobre lo que se podría suponer**: el gerente también puede formalizar hoy mismo
(`GENERAR_DOCUMENTOS_FORMALIZACION = TOTAL` para GERENTE_ADMINISTRADOR, igual que para el
oficial, verificado en `ServicioAutorizacion.construirMatriz`) — no hace falta ningún permiso
nuevo para un botón "Formalizar ahora" en el aviso de aprobación (ver Hallazgos en
`03_aprobacion.md`).

## A confirmar

- Si, en la prueba de hoy, el recálculo diario de mora (§1) ya había corrido antes de que el
  gerente abriera el panel (porque el oficial pasó primero por `/panel`/`/mora`) o si corrió en
  ese momento — no lo determiné sin consultar la base.
- El detalle exacto de qué lee `ServicioFormalizacion.listarListosParaDesembolsar` y
  `ServicioCaja.calcularFondoSugerido` (usados por el indicador "Pendientes" del panel) — los
  describí por el patrón ya conocido de esos servicios (`prestamos` + checklist de
  `documentos_expediente`; `sesiones_caja`/`movimientos_caja`), sin releer sus métodos línea por
  línea en esta sesión.
