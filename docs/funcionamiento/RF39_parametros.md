# RF39 — Parámetros del negocio, feriados y límites legales de tasa, con historial

> **Estado: en análisis (13/10/2026).** Esta sesión es de solo lectura — ningún archivo de código
> ni de base de datos fue modificado. Todo lo que sigue viene de leer el código y de `SELECT`
> reales sobre `sgcpc_db_pruebas` (nunca `sgcpc_db`).

## 1. Qué exige el anteproyecto

RF39: "El sistema debe permitir al administrador modificar los parámetros del negocio (tasa,
montos, plazos y límite de afectación), los feriados y los límites legales de tasa, y guardar el
historial de cada cambio con el valor anterior, el valor nuevo, quién lo hizo y cuándo."

Tres grupos de datos distintos, los tres editables desde la misma pantalla (`/parametros`,
`administracion.jsp`, `ControladorParametrosServlet`):

1. **Parámetros del negocio** (tabla `parametros_sistema`, 54 claves vigentes hoy).
2. **Feriados** (tabla `feriados`).
3. **Límites legales de tasa** (tabla `limites_tasa_usura`).

## 2. Cómo funciona

### 2a. Parámetros del negocio — append-only con vigencia

`parametros_sistema` nunca hace `UPDATE` sobre un valor: cada cambio es una fila nueva.
Columnas: `clave_parametro`, `nombre_parametro`, `valor_parametro` (siempre texto, `varchar(500)`),
`tipo_valor` (TEXTO/ENTERO/DECIMAL/BOOLEANO/FECHA), `vigente_desde`, `vigente_hasta` (`NULL` =
fila vigente hoy), `modificado_por`, `fecha_modificacion`, `observacion` (`varchar(300)`,
nullable).

`ServicioParametros.actualizarValor` (`ServicioParametros.java:279-312`) es el método que hace el
cambio, en una sola transacción:
1. Lee la fila vigente (`ParametroDAO.obtenerVigentePorClave`).
2. Interpreta lo escrito según el tipo (`normalizarValorNuevo`, línea 315 en adelante — acepta
   coma o punto decimal; los DECIMAL se escriben como porcentaje y se guardan como fracción).
3. Valida (`validarFraccionSegunClave`/`validarEnteroSegunClave`/etc., con los `validar*`
   específicos de líneas 191-268 — ver §6).
4. Cierra la vigencia anterior (`vigente_hasta = hoy`).
5. Inserta la fila nueva.
6. Audita con `servicioAuditoria.registrarCambioDeValor(...)` (línea 302-304), que guarda
   **valor anterior y valor nuevo** en `auditoria_operaciones.valor_anterior`/`valor_nuevo`
   (columnas JSON, vía `AuditoriaDAO.jsonDeValor`).

### 2b. Feriados — alta y activar/desactivar, sin vigencia propia

`feriados` es una tabla simple: `id_feriado`, `fecha`, `descripcion`, `activo` — **sin**
`vigente_desde`/`vigente_hasta`/`modificado_por`/`observacion`. El alta es
`ServicioParametros.altaFeriado` y el único "cambio" posible después de cargado es
`cambiarActivoFeriado` (activar/desactivar, no se edita fecha ni descripción ni se borra la fila
— coherente con la regla de inmutabilidad del proyecto).

### 2c. Límites legales de tasa — también append-only con vigencia propia

`limites_tasa_usura` sí tiene su propio patrón de vigencia: `id_limite_tasa`, `vigente_desde`,
`vigente_hasta`, `tasa_maxima_tea`, `fuente_referencia` (texto libre, p. ej. "Tope BCP vigente
sept. 2026"), `registrado_por`, `fecha_registro`. `ServicioParametros.altaLimiteTasa`
(líneas 512-542): si se carga un límite nuevo "abierto" (`vigente_hasta = NULL`) y ya había uno
vigente, **cierra solo el anterior** (pone su `vigente_hasta` el día antes del nuevo `vigente_desde`)
y valida que no se superpongan vigencias (`validarSuperposicionLimitesTasa`, líneas 257-268).

Hoy (`sgcpc_db_pruebas`) hay un solo registro vigente: `tasa_maxima_tea = 0.2739` (27,39 %),
`fuente_referencia = "Tope BCP vigente sept. 2026"`, cargado el 08/10/2026 por el usuario 1.

### 2d. Dos lugares distintos para "el mismo" tope legal — sin vínculo en código

Hay **dos** valores separados que representan el tope legal de la TEA, y no están conectados por
ningún código:
- `parametros_sistema` → clave `credito.tea_tope_legal` (hoy: `0.273900`) — es el valor que
  **`validarTeaEmpresa` usa de verdad** para rechazar un cambio de `credito.tea_empresa`
  (`ServicioParametros.java:419`, `obtenerValorDecimal(conexion, "credito.tea_tope_legal")`).
- `limites_tasa_usura` → fila vigente con `tasa_maxima_tea` (hoy también `0.2739`, por carga
  manual coincidente, no porque un código copie uno al otro).

`altaLimiteTasa` **no** actualiza `credito.tea_tope_legal`, y `actualizarValor` sobre
`credito.tea_tope_legal` **no** toca `limites_tasa_usura`. Hoy coinciden por casualidad (ambos en
0,2739) pero un administrador podría cambiar uno sin el otro y la validación de
`credito.tea_empresa` seguiría comparando solo contra `credito.tea_tope_legal`, sin enterarse del
cambio en `limites_tasa_usura`. No es uno de los 4 pendientes que Joel ya conocía; se deja
anotado en §10 como hallazgo nuevo de esta sesión, de bajo riesgo (el valor vigente de ambos
coincide hoy).

## 3. Auditoría

| Acción | Tabla / método | Queda en `auditoria_operaciones` | Queda en el historial de `/parametros` |
|---|---|---|---|
| Cambiar un parámetro | `ServicioParametros.actualizarValor` | Sí — `CAMBIAR_PARAMETRO`, con `valor_anterior`/`valor_nuevo` | Sí — `ParametroDAO.listarHistorial` (LAG) |
| Alta de feriado | `altaFeriado` | Sí (confirmar operación exacta, ver §6b) | No — `feriados` no tiene columnas de vigencia |
| Activar/desactivar feriado | `cambiarActivoFeriado` | Sí | No |
| Alta de límite de tasa | `altaLimiteTasa` | Sí — `ALTA_LIMITE_TASA`, con detalle de texto (tasa + fecha) | No — `/parametros` no tiene una pantalla de "historial de límites de tasa"; la vigencia queda en la propia tabla (`vigente_desde`/`vigente_hasta`), consultable por `SELECT`, pero no hay un listado en pantalla con el patrón `valor_anterior`/`valor_nuevo` como el de parámetros |

**Conclusión de §3**: el historial con el patrón exacto que pide el anteproyecto (valor
anterior, valor nuevo, quién, cuándo, en una pantalla) **solo existe para los parámetros del
negocio**. Feriados y límites de tasa quedan rastreables (auditoría general, o la propia tabla
con `SELECT`), pero no con una pantalla de historial dedicada. Ver §6b.

## 4. Dónde está

- Pantalla única: `/parametros` → `src/main/webapp/WEB-INF/vistas/parametros/administracion.jsp`
  (alta/cambio de los 3 grupos) y `src/main/webapp/WEB-INF/vistas/parametros/historial.jsp`
  (`/parametros/historial`, solo parámetros del negocio).
- Controlador: `ControladorParametrosServlet.java`.
- Servicio: `ServicioParametros.java`.
- DAO: `ParametroDAO.java`, `FeriadoDAO.java`, `LimiteTasaUsuraDAO.java`.
- Permiso: `ADMINISTRAR_PARAMETROS` (ver `ServicioAutorizacion.construirMatriz()` — solo
  administrador tiene `TOTAL`; gerente tiene `CONSULTA`; oficial/cajera `NINGUNO`).

## 5. Dependencias

- `ServicioMotorFinanciero`, `ServicioCotizacion`, `ServicioSolicitudes`, `ServicioClientes`,
  `ServicioFormalizacion`, `ServicioCaja`, `ServicioCobranza`, `ServicioMora`,
  `ServicioReversiones`, `ServicioDesembolso`, `ServicioConsultaCrediticia`,
  `ServicioDocumentos`: todos leen uno o más parámetros en caliente (ver la tabla de §6a para el
  detalle completo por clave).
- `REGLAS_DESARROLLO.md` §5 — valores de negocio que "nunca se rompen", usados como referencia
  de comparación en §6a.

## 6. Análisis

### 6a. Tabla de parámetros del negocio vs. `REGLAS_DESARROLLO.md` §5

Valor en `sgcpc_db_pruebas` tomado por `SELECT` el 13/10/2026 (54 filas vigentes,
`observacion` vacía en las 54 — ver §6c). Solo se listan las claves con una regla de negocio
comparable en REGLAS §5; el resto (identificación de la empresa, datos fiscales, seguridad
operativa) se detalla en la nota al pie de la tabla.

| Clave | Nombre visible | Valor vigente | Código que lo usa | ¿Coincide con REGLAS §5? |
|---|---|---|---|---|
| `credito.tea_empresa` | TEA de la empresa | 0,27 (27 %) | `ServicioCotizacion`, `ServicioSolicitudes`, `ServicioParametros` | **Sí** — REGLAS §5 pide 27 % |
| `credito.tea_tope_legal` | TEA tope legal | 0,2739 (27,39 %) | `ServicioParametros` (cota de `validarTeaEmpresa`) | — (no es una de las 8 reglas listadas en §5; ver §2d) |
| `credito.monto_minimo` | Monto mínimo de crédito (Gs.) | 500.000 | `ControladorCotizadorServlet`, `ServicioClientes`, `ServicioCotizacion`, `ServicioSolicitudes`, `ServicioParametros` | **Sí** — REGLAS §5 pide 500.000 |
| `credito.monto_maximo` | Monto máximo de crédito (Gs.) | 15.000.000 | (mismos archivos que arriba) | **Sí** — REGLAS §5 pide 15.000.000 |
| `credito.plazo_minimo` | Plazo mínimo (meses) | 3 | (mismos archivos) | **Sí** — REGLAS §5 pide 3 |
| `credito.plazo_maximo` | Plazo máximo (meses) | 24 | (mismos archivos) | **Sí** — REGLAS §5 pide 24 |
| `credito.limite_afectacion` | Límite de afectación al ingreso | 0,40 (40 %) | `ControladorSolicitudesServlet`, `ServicioFormalizacion`, `ServicioSolicitudes`, `ServicioParametros` | **Sí** — REGLAS §5 pide ≤ 40 % |
| `credito.edad_minima` | Edad mínima para otorgar crédito | 22 | `ControladorClientesServlet`, `ServicioClientes`, `ServicioSolicitudes` | **Sí** — REGLAS §5 pide 22 |
| `credito.edad_maxima` | Edad máxima para otorgar crédito | 71 | (mismos archivos) | **Sí** — REGLAS §5 pide 71 |
| `credito.exigir_garante` | Exigir garante/codeudor | "nunca" | Huérfano desde el retiro de codeudor (18/10/2026) — ningún servicio ni controlador lo lee; pendiente de eliminar en el Commit 3 del refactor | **Sí** — REGLAS §5 pide "nunca" (y ya no se exige garante bajo ninguna condición) |
| `credito.departamentos_habilitados` | Departamentos habilitados | "Asunción,Central" | `ControladorClientesServlet`, `ServicioClientes`, `ServicioSolicitudes` | **Sí** — REGLAS §5 pide Asunción/Central |
| `formalizacion.dias_min_primer_vencimiento` | Días mínimos primer vencimiento | 20 | `ServicioCotizacion`, `ServicioFormalizacion`, `ServicioSolicitudes`, `ServicioParametros` | **Sí** — REGLAS §5 pide 20 |
| `formalizacion.dias_max_primer_vencimiento` | Días máximos primer vencimiento | 45 | (mismos archivos) | **Sí** — REGLAS §5 pide 45 |

**Resultado: los 8 valores de REGLAS §5 coinciden exactamente con `sgcpc_db_pruebas` hoy.** Sin
discrepancias.

Resto de claves (no comparadas contra REGLAS §5 porque no tienen una regla ahí — son operativas o
institucionales): `archivos.tamano_maximo_mb` (`ServicioDocumentos`), `buro.dias_vigencia_consentimiento`
(`ServicioConsultaCrediticia`, `ConsultaCrediticiaDAO`), `caja.diferencia_tolerada`/
`caja.gerente_predeterminado`/`caja.monto_maximo_arqueo` (`ServicioCaja`), `credito.cuotas_alerta_garante`
(ver §6e), `credito.dias_gracia_mora` (`ServicioCobranza`, `ServicioReversiones`),
`credito.dias_incobrable` (`ServicioMora`), `credito.factor_moratorio`/`credito.porcentaje_punitorio`
(`ServicioFormalizacion`, `ServicioParametros`), `credito.redondeo_efectivo` (`ServicioCobranza`),
`credito.situaciones_laborales_admitidas` (`ServicioClientes`, `ServicioSolicitudes`),
`seguridad.intentos_login`/`seguridad.minutos_bloqueo`/`seguridad.minutos_inactividad`
(`ControladorAutenticacionServlet`, `FiltroAutenticacion`), 13 claves `empresa.*`/`fiscal.*`
(datos institucionales y fiscales — usados en `GeneradorInformesPdf`, `ServicioInformes` y los
servicios que emiten comprobantes).

### 6b. Feriados y límites de tasa: dónde se modifican, si guardan historial

Ambos se modifican en la misma pantalla que los parámetros (`/parametros`,
`ControladorParametrosServlet.java:98-112`) — no hay una pantalla separada. Ninguno de los dos
tiene una pantalla de "historial" dedicada (ver §3). `feriados` no tiene ninguna columna de
vigencia; `limites_tasa_usura` sí tiene su propia vigencia (`vigente_desde`/`vigente_hasta`) pero
el "historial" en ese caso es simplemente consultar la tabla completa (`listarLimitesTasa`), no
una reconstrucción tipo `LAG()` como la de parámetros.

### 6c. Historial: ¿registra valor anterior, valor nuevo, usuario y fecha?

**Sí, para parámetros del negocio — comprobado con datos reales.** `ParametroDAO.listarHistorial`
(líneas 153-199) reconstruye el valor anterior con una función de ventana
`LAG(p.valor_parametro) OVER (PARTITION BY p.clave_parametro ORDER BY p.vigente_desde,
p.fecha_modificacion, p.id_parametro)`, y trae `usuario` (join con `usuarios`) y
`fecha_modificacion`. Ejemplo real, `credito.exigir_garante` (13 cambios reales del 08 y
12/10/2026, hechos durante las pruebas de RF10):

| valor_parametro | valor_anterior | usuario | fecha_modificacion |
|---|---|---|---|
| nunca | *(ninguno — carga inicial)* | Ramón Benítez | 2026-10-08 05:28:19 |
| siempre | nunca | Joel Jesús Acosta | 2026-10-12 09:51:10 |
| nunca | siempre | Joel Jesús Acosta | 2026-10-12 09:51:23 |
| siempre | nunca | Joel Jesús Acosta | 2026-10-12 09:51:57 |
| nunca | siempre | Joel Jesús Acosta | 2026-10-12 09:52:24 |
| *(… 8 filas más, mismo patrón alternado …)* | | | |
| nunca | siempre | Joel Jesús Acosta | 2026-10-12 15:15:51 *(vigente hoy)* |

Cada fila trae también `observacion` — y **las 54 filas vigentes hoy, y todas las filas de este
ejemplo, tienen `observacion` vacía** (confirmado por `SELECT`). Ver §6d (causa) y §10.

**Esto está limitado solo a `parametros_sistema`** (ver §3): la misma reconstrucción con `LAG()`
no existe para `feriados` ni para `limites_tasa_usura`.

### 6d. Causa de "Observación siempre vacía" (ya conocido por Joel, confirmada la causa exacta)

`ServicioParametros.actualizarValor` (línea 299): `nuevo.setObservacion(actual.getObservacion())`
— al crear la fila nueva, **copia la observación de la fila anterior** en vez de recibir una
observación nueva del formulario. Como la fila anterior también tenía `observacion` vacía (porque
nunca se pidió), la cadena entera queda vacía desde la siembra inicial. La columna y el DTO están
completamente conectados de punta a punta (`ParametroDAO.insertar`, línea 201-205, sí guarda
`observacion` si se le pasa una) — lo único que falta es: 1) un campo en `administracion.jsp` para
escribirla, y 2) que `actualizarValor` reciba ese texto en vez de copiar el de la fila anterior.
No implementado en esta sesión (sin cambios de código).

### 6e. `credito.cuotas_alerta_garante`: nombre engañoso, uso real confirmado

Ya investigado a fondo en la sesión de RF38 (ver `LEEME.md`, nota del 13/10/2026): el parámetro
se usaba en 2 lugares. `ServicioMora.construirFilas` lo usaba para la alerta de garante en
`/mora` — eliminado por completo con el retiro de codeudor/garante (18/10/2026), ver
`RF24_seguimiento_mora.md` §5/§7. Queda `ServicioFormalizacion` (líneas 903, 1144, 1192), que lo
usa **sin relación con el garante**, para la cláusula OCTAVA del contrato (vencimiento
anticipado por mora) y `pagare.jrxml` (línea 119) para el texto equivalente del pagaré — textos
que se imprimen en todo contrato y pagaré. No se puede quitar el parámetro sin romper la
generación de ambos documentos. Ver §10 para la corrección propuesta (solo el nombre visible).

### 6f. `caja.monto_maximo_arqueo` sin formato de miles/Gs.

Ya investigado en la sesión de RF38: se muestra como "500000000" en vez de "Gs. 500.000.000".
`administracion.jsp` (líneas 51-52) tiene una lista fija `clavesGuaranies` (hoy:
`credito.monto_minimo`, `credito.monto_maximo`, `caja.diferencia_tolerada`) que decide qué
parámetros ENTERO se formatean como monto en guaraníes; `caja.monto_maximo_arqueo` no está en esa
lista. Ver §10.

### 6g. El tag `rf37-rf39-probado`

Investigado en la sesión de RF38 (commit `6a7e8af`, 07/10/2026): es un checkpoint informal,
anterior a la disciplina actual de un documento por RF, sin commit propio de RF39 cerca y sin
nota correspondiente en `AVANCE.md`. **No cuenta como prueba de RF39.** El primer recorrido real
de RF39 contra el anteproyecto es el de esta sesión (13/10/2026, etapa de análisis).

### 6h. Dos registros separados del mismo tope legal (hallazgo nuevo de esta sesión)

Ver §2d. Riesgo bajo hoy (ambos valores coinciden), documentado como pendiente nuevo en §10.

## 7. Decisiones

- **D1** — motivo obligatorio (mínimo 10 caracteres) al cambiar un parámetro; se guarda en
  `observacion` y se muestra en `/parametros/historial`.
- **D2** — validaciones nuevas en el servidor: TEA > 0 y ≤ tope legal; montos > 0 y mínimo <
  máximo (se mantuvo el `<` estricto, no se aflojó a `≤`); edad mínima < máxima (nueva, no
  existía); límite de afectación entre 0 % (exclusivo) y 100 % ya estaba correcto, sin cambios.
- **D3** — `caja.monto_maximo_arqueo` con formato de guaraníes en `administracion.jsp`.
- **E1** — una sola fuente para el tope legal de TEA: `limites_tasa_usura` (ya usada por
  cotizador/aprobación/formalización). `credito.tea_tope_legal` retirado: vigencia cerrada en
  `sgcpc_db_pruebas` (migración `E10_15_...sql`) y quitado de la siembra (`bd/03_parametros.sql`).
- **E2** — el detalle de auditoría de feriados y límites de tasa ahora dice el antes y el
  después (alta/activar/desactivar feriado; alta de límite de tasa).
- **E3** — `caja.monto_maximo_arqueo` agregado también a la lista de guaraníes del historial/PDF/
  CSV (`ServicioParametros.CLAVES_GUARANIES`); de paso, se corrigió una regresión propia de D3
  (la clase `campo-guaranies` del input rompía el guardado sin agregar la clave también a
  `CLAVES_ENTERO_GUARANIES`).
- **E4** — nombre visible de `credito.cuotas_alerta_garante` → "Cuotas vencidas para el
  vencimiento anticipado" (clave y `.jrxml` sin tocar).
- **E5** — `pruebas/humo_http.sh` manda `motivo=Prueba automática de humo` en cada
  `actualizar-valor`, y verifica por `SELECT` que el valor realmente cambió (no solo el código
  HTTP). Modificado pero **todavía no ejecutado** — ver §10.

## 8. Historial de cambios

| Fecha | Acción | Commit | Tag |
|---|---|---|---|
| 16/11/2026 | **Toda la pantalla probada en pantalla por Joel** (B219 *“164 cambios / Página 1 de 9”*, B220 agrupado + buscador + píldoras en un renglón, B221 *Próximos* desde el 08/12 y *Ya pasados* descendente, B223/B224 con el tope real sin cambios). **Agregado el 16/11 a pedido suyo**: la sección *“Ya pasados”* de Feriados arranca **plegada**, con un botón *“Ver los 10 ya pasados”*. **Siguen “no vistos”, verificado en la base**: B217 (los 24 feriados están activos), B218 (`redondeo_efectivo` sin historial) y los dos escenarios de B223/B224 (`limites_tasa_usura` con una sola fila) | *(este commit)* | — |
| 15/11/2026 | **B220-bis** (pedido de Joel al probar B220: la página seguía demasiado larga): **píldoras de grupo** arriba de la tabla, que filtran sin recargar — mismo patrón y mismo `indice-reportes.css` que las categorías del índice de `/reportes`, con el orden decidido en `ServicioParametros.gruposConEtiqueta()` — y **filas compactas**: la fila muestra parámetro, valor y *Vigente desde* con un botón *“Cambiar”* que despliega su formulario debajo, **uno a la vez**. El buscador y el contador siguen siendo los de V11 pero la **N es la del grupo** elegido, y el grupo se **recuerda** entre recargas. Los formularios **siguen en el HTML** (ocultos por CSS): llevan el token que `pruebas/humo_http.sh` lee del `GET /parametros` para sus cuatro POST | *(este commit)* | — |
| 15/11/2026 | **El tope legal, los dos juntos porque son el mismo dato**: **B224** — *vigente en una fecha* pasa a ser *ya empezó **y** todavía no terminó*, en el método puro `LimiteTasaUsuraDAO.elVigenteEn` (antes la consulta no miraba la fecha, así que un tope con `vigente_desde` futuro regía hoy); los **7 lectores** revisados, ninguno lee `vigente_hasta` y ninguna firma cambió. **B223 opción C** — un límite cargado con la tasa equivocada se puede **reemplazar el mismo día**: el existente se cierra el día anterior a su propio inicio (vigencia vacía, *nunca rigió*), un rango vacío no se superpone con nada, y la pantalla lo muestra como *“No rigió (reemplazado)”*. **14 JUnit, 5 vistos en rojo** contra las reglas viejas. La **opción A** (corregir en su lugar) queda como **B227**, para después de la entrega | *(este commit)* | — |
| 15/11/2026 | **Tanda 2 de Seguridad**: **B219** el historial de cambios **pagina** de 20 en 20 (`PaginacionPantalla`, nueva, con 6 JUnit; los totales se calculan antes de recortar y el PDF/CSV siguen exportando todo), **B220** los 52 parámetros van **agrupados por el prefijo de la clave** (`ServicioParametros.grupoDeClave`, con su JUnit leyendo `bd/03_parametros.sql`) y con **buscador en tabla**, **B221** los feriados muestran los **próximos primero** con el corte *“Próximos”* / *“Ya pasados”* (`comparadorFeriados(hoy)`, 4 JUnit). **Sin implementar, esperando decisión de Joel**: **B223** (límites de tasa: corregir vs. anular vs. destrabar el reemplazo del mismo día — las opciones están en `AVANCE.md` 15/11, XXIII) y **B224** (`obtenerVigente` no compara la fecha, así que un tope con `vigente_desde` futuro se aplica desde hoy) | *(este commit)* | — |
| 15/11/2026 | **B215 probado en pantalla por Joel**, y el defecto que encontró ahí: *“Actual: 500.000.000 Gs..”* con dos puntos en los cinco parámetros en guaraníes — corregido con `FormateadorTexto.conPuntoFinal` (agrega el punto solo si falta) y cubierto por una red nueva en `TextosDePantallaTest`, la **quinta** forma de ese defecto. **B218**: `credito.redondeo_efectivo` entra en `CLAVES_ENTERO_GUARANIES`, así que ya admite 1.000 o más; `ParametrosEnGuaraniesTest` lee la lista de `administracion.jsp` y la cruza con la del servicio para que no vuelvan a divergir | *(este commit)* | — |
| 15/11/2026 | **B215**: debajo del campo *“Cambiar”* la pantalla dice *“Actual: X”* (con el mismo formato de la columna *Valor*, y solo en DECIMAL/ENTERO/FECHA) y la **regla real** de esa clave, que la da `ServicioParametros.reglaDeClave` — las **17** claves con validación cruzada de D1/D2/D3/D-84/D-89; las otras 35 no prometen nada. `ServicioParametrosReglaTest` cruza las dos listas leyendo el propio `ServicioParametros.java`. **B217**: *“Activar”* un feriado también confirma, con el molde de *“Desactivar”*. **B218 anotado, sin aplicar**: `credito.redondeo_efectivo` se escribe con puntos de miles en la pantalla y `CLAVES_ENTERO_GUARANIES` no lo incluye, así que no admite 1.000 o más. Ver `FASE2_PLAN.md` § Seguridad — tanda 1 | *(este commit)* | — |
| 13/10/2026 | Análisis de solo lectura de RF39 (parámetros, feriados, límites de tasa) contra REGLAS §5 y el anteproyecto; creación de este documento y actualización de `LEEME.md` | `bd1d4bd` | — |
| 13/10/2026 | Implementado D1 (motivo obligatorio), D2 (validaciones nuevas), D3 (formato de `caja.monto_maximo_arqueo`); `mvn test` en verde. | — | — |
| 13/10/2026 | Joel aprobó las propuestas de E1 (tope legal única fuente)/E2 (historial feriados y límites)/E3 (D3 completo)/E4 (nombre de `cuotas_alerta_garante`)/E5 (`humo_http.sh`); implementados los 5; `mvn test` en verde. | — | — |
| 13/10/2026 | Joel aprobó el comando de migración; aplicado `bd/migraciones/E10_15_rf39_tope_legal_y_nombre_parametro.sql` en `sgcpc_db_pruebas` (verificado por `SELECT`); `bd/03_parametros.sql` actualizado para que una base nueva nazca sin `credito.tea_tope_legal` vigente. | — | — |
| 13/10/2026 | Probado en pantalla por Joel, después de su Clean and Build (ver §9). | `2f00be7` | `rf39-probado` |
| 13/10/2026 | Correcciones 2a (aviso de D2 como advertencia) y 2b (detalle de auditoría con motivo) — `ControladorParametrosServlet.java`, `ServicioParametros.java`; `mvn test` en verde. | — | — |
| 13/10/2026 | 2a/2b probadas en pantalla y aprobadas por Joel, después de su Clean and Build (ver §9). Tag `rf39-probado` movido a este commit (estado probado completo). | *(este commit)* | `rf39-probado` (movido) |

## 9. Pruebas realizadas en pantalla

Probado en pantalla por Joel el 13/10/2026, en `sgcpc_db_pruebas`, después de su Clean and
Build:

- **Parámetros (administrador)**: nombre visible "Cuotas vencidas para el vencimiento
  anticipado" (E4); `caja.monto_maximo_arqueo` muestra "500.000.000 Gs." (D3/E3);
  `credito.tea_tope_legal` ya no aparece en la pestaña (E1).
- **D1**: cambiar un parámetro sin motivo → "Falta un dato" / "Falta completar «Motivo del
  cambio»" / "Entendido".
- **D2**: TEA 0 → rechazada por el servidor, "La TEA de la empresa debe ser mayor que 0 %."
- **D1 + historial**: TEA 27 → 26 → 27, cada vez con motivo → "Parámetro actualizado" /
  "Aceptar"; `/parametros/historial` muestra fecha y hora, valor anterior → nuevo, usuario y
  motivo de cada cambio.
- **E2**: feriado 16/03/2027 "Prueba RF39" — alta, desactivar, activar, desactivar; en
  `/auditoria` las 4 operaciones (ALTA/DESACTIVAR/ACTIVAR/DESACTIVAR) muestran el antes → después.
  El feriado quedó inactivo (estado final de la prueba, esperado).
- **Permiso**: gerente ve `/parametros` en consulta, con el aviso "Solo el administrador puede
  modificar los parámetros", sin campos ni botón "Guardar".
- **No probado en pantalla, a propósito**: alta de un límite de tasa (para no cambiar el tope
  legal vigente de `sgcpc_db_pruebas`) — queda cubierto por las pruebas JUnit de
  `construirDetalleAltaLimiteTasa` (§6/E2).

**Correcciones 2a/2b, probadas en pantalla por Joel el 13/10/2026, después de su Clean and
Build:**

- **2a**: TEA 0 → icono "!", título "No se puede guardar", mensaje "La TEA de la empresa debe ser
  mayor que 0 %." debajo, botón "Entendido" (antes: icono de error/X roja, con el mensaje
  completo como título).
- **2b**: `archivos.tamano_maximo_mb` de 5 a 6 (con motivo) y vuelta a 5 (con motivo) — en
  `/auditoria`, `CAMBIAR_PARAMETRO` muestra 'Cambió "Tamaño máximo de archivo (MB)": 5 → 6.
  Motivo: Prueba RF39: auditoría.' y el regreso simétrico 6 → 5 con su propio motivo (antes:
  solo decía `Cambió "archivos.tamano_maximo_mb".`, sin valores ni motivo). El parámetro quedó
  en 5 (su valor original) al cerrar la prueba.

**14/10/2026, Tanda 2, probado en pantalla por Joel** — tag `mejoras-tanda2-probado`:
"Redondeo del efectivo…" muestra "50 Gs." (antes "50"), con el campo del historial "valor nuevo
(Gs.)" también formateado. **OK.**

**14/10/2026, caso real confirmado por Joel (piloto L2-B): "vigente a la fecha del documento",
no un caché.** Joel cambió `empresa.ruc` de `80123456-7` a `80123456-5` a las 10:10 (con motivo).
`SELECT` en `sgcpc_db_pruebas`:

| id_parametro | valor | vigente_desde | vigente_hasta |
|---|---|---|---|
| 3 | 80123456-7 | 2026-10-08 | 2026-10-14 |
| 85 | 80123456-5 | 2026-10-14 | *(ninguno)* |

- **Constancia de reversión REV-2026-000003** (fecha del documento: 12/10/2026, antes del
  cambio) → salió con el RUC **viejo** (80123456-7): correcto, los comprobantes reimprimen con
  `parametrosEmpresaAFecha` (la fecha del documento, nunca la de hoy — D-94,
  `ServicioCobranza.java:1823-1836`).
- **Cotización generada a las 10:24** (después del cambio, nunca se guarda — RF-33) → salió con
  el RUC **nuevo** (80123456-5): correcto, usa `GeneradorInformesPdf.parametrosBase`
  (`obtenerValor`, sin fecha, siempre el vigente hoy).

Se descartó un caché en memoria: `ServicioParametros`/`ParametroDAO` no tienen ningún `static Map`
— cada llamada (`obtenerValor`/`obtenerValorAFecha`/`obtenerValorAFechaOMasAntiguo`) ejecuta un
`SELECT` nuevo sobre la `Connection` recibida. Ver el detalle completo en
`docs/funcionamiento/procesos/lote2_estilo_pdf.md` ("Regla D-94 confirmada con un caso real").

## 10. Pendientes

**Resueltos en esta sesión** (los 4 que Joel ya conocía + el hallazgo nuevo + las 2 validaciones
que faltaban), todos probados en pantalla salvo donde se indica:

1. ~~Observación del historial siempre vacía~~ — **resuelto (D1)**: motivo obligatorio, se guarda
   en `observacion`. Probado en pantalla.
2. ~~`credito.cuotas_alerta_garante`: nombre visible engañoso~~ — **resuelto (E4)**: nombre nuevo
   "Cuotas vencidas para el vencimiento anticipado" aplicado en `sgcpc_db_pruebas`
   (`bd/migraciones/E10_15_...sql`) y en la siembra (`bd/03_parametros.sql`). Probado en pantalla.
3. ~~`caja.monto_maximo_arqueo` sin formato de miles/Gs.~~ — **resuelto (D3/E3)**: agregado a las
   2 listas de guaraníes (`administracion.jsp` y `ServicioParametros.CLAVES_GUARANIES`). Probado
   en pantalla.
4. **Tag `rf37-rf39-probado` no cuenta como prueba** — sigue igual, no es un pendiente de código
   (ver §6g); queda como nota histórica, no como algo por corregir.
5. ~~Dos registros separados para el mismo tope legal de TEA~~ — **resuelto (E1)**:
   `limites_tasa_usura` es la única fuente; `credito.tea_tope_legal` retirado. Probado en
   pantalla (ya no aparece en `/parametros`).
6. ~~TEA 0 sería aceptada~~ — **resuelto (D2)**: `validarTeaEmpresa` rechaza `<= 0`. Probado en
   pantalla.
7. ~~Monto negativo sería aceptado~~ — **resuelto (D2)**: `validarMontos` rechaza `<= 0` en ambos
   extremos. **No probado en pantalla** (Joel probó TEA 0; monto negativo queda cubierto solo por
   JUnit, `testMontoMinimoOMaximoNegativoOCeroSeRechaza`).

**Encontrados en la prueba en pantalla del 13/10/2026, corregidos y probados (2a/2b):**

8. ~~Rechazo de D2 mostrado como error (X roja, mensaje como título) en vez de advertencia.~~ —
   **resuelto (2a)**: `ControladorParametrosServlet` usaba `MensajesFlash.guardarError` para
   cualquier `NegocioException` de `/actualizar-valor` — un bloqueo de negocio no es un error del
   servidor. Ahora usa el mismo mecanismo que `ControladorClientesServlet` (prefijo "No se puede
   guardar. " + `guardarAdvertencia`): icono "!", título corto, mensaje debajo, "Entendido".
   Probado en pantalla.
9. ~~Detalle de auditoría de `CAMBIAR_PARAMETRO` sin valor anterior/nuevo/motivo.~~ — **resuelto
   (2b)**: antes solo decía `Cambió "clave".`; ahora sigue el mismo patrón que E2 (feriados/
   límites de tasa) con el antes → después y el motivo. Probado en pantalla.

**Encontrados por Joel en esta misma prueba, anotados sin implementar (pedido explícito: solo
registrar, no corregir todavía)**:

10. ~~**"Redondeo del efectivo a favor del cliente" (`credito.redondeo_efectivo`) se muestra sin
    "Gs."**~~ — **resuelto y probado en pantalla el 14/10/2026** (Tanda 2, tag
    `mejoras-tanda2-probado`, ver §9): agregado a las 2 listas de guaraníes, mismo arreglo que
    `caja.monto_maximo_arqueo`.
11. **"Representante legal" vive en 3 claves separadas** — `empresa.representante_legal`
    (combinada, "Ramón Benítez – C.I. 2.845.117"), `empresa.representante_legal_nombre` (solo
    nombre) y `empresa.representante_legal_cedula` (solo cédula). Investigado dónde se usa cada
    una: la combinada **solo** la usa `ServicioFormalizacion.java:1156` (`EMPRESA_REPRESENTANTE`,
    un reporte); nombre y cédula por separado las usan **3 lugares**: devolución del pagaré
    (`ServicioFormalizacion.java:1013/1015`), cancelación (`ServicioCobranza.java:2217/2219`) y
    anulación (`ServicioCobranza.java:2297/2299`). Las 3 claves están en uso — ninguna sobra en el
    sentido de "no se lee en ningún lado" — pero la combinada se usa en 1 solo lugar contra 3 de
    la pareja separada, así que podría derivarse de nombre+cédula en vez de mantenerse como un
    tercer valor en paralelo (riesgo de que alguien actualice nombre/cédula y se olvide de la
    combinada, quedando desincronizada). No corregido — queda a criterio de Joel.

**E5 (`humo_http.sh`) — modificado pero todavía NO ejecutado.** Se agregó el campo `motivo` a
`actualizar_parametro_como_admin` y una verificación por `SELECT` después de los 6 cambios de
parámetro con su propio `registrar`. Sintaxis verificada con `bash -n`, pero la corrida completa
(gerente + oficial + cajera) queda pendiente — Joel decide cuándo correrla.

## 11. Guion para la defensa / guion de prueba en pantalla propuesto

**Ejecutado y aprobado por Joel el 13/10/2026** (ver §9 para el detalle completo). El guion
original (4 pasos) se cumplió, con 2 agregados que Joel incorporó en la misma prueba:

1. **Administrador cambia un parámetro con motivo y lo devuelve.** En `/parametros`, cambiar
   `credito.tea_empresa` de 27 % a 26 % (con motivo) y volver a cambiarlo a 27 % (con motivo).
   ✅ Hecho.
2. **Se ve en el historial, con el motivo.** `/parametros/historial` muestra fecha y hora, valor
   anterior → nuevo, usuario y motivo de cada cambio. ✅ Hecho (el motivo es nuevo respecto al
   guion original, D1).
3. **Sin motivo → aviso.** Intentar cambiar un parámetro sin escribir el motivo → "Falta un
   dato" / "Falta completar «Motivo del cambio»" / "Entendido". ✅ Hecho (agregado por D1, no
   estaba en el guion original).
4. **TEA 0 → rechazado por el servidor.** "La TEA de la empresa debe ser mayor que 0 %." ✅ Hecho
   (a diferencia de lo que decía la versión anterior de este documento, SÍ se probó en pantalla:
   quedó resuelto por D2 antes de la prueba).
5. **El gerente ve en consulta y no puede modificar.** `/parametros` en consulta, con el aviso
   "Solo el administrador puede modificar los parámetros", sin campos ni botón "Guardar". ✅
   Hecho.
6. **Feriados con antes→después (E2, agregado).** Alta, desactivar, activar, desactivar un
   feriado de prueba; `/auditoria` muestra las 4 operaciones con el antes → después. ✅ Hecho.

**No probado a propósito**: alta de un límite de tasa (cambiaría el tope legal vigente de
`sgcpc_db_pruebas`, usado por el cotizador/aprobación/formalización) — cubierto solo por JUnit.
**No probado todavía**: monto negativo (D2) — cubierto solo por JUnit. Las 2 correcciones chicas
encontradas en esta misma prueba (2a/2b, ver §10 puntos 8/9) ya fueron implementadas y también
probadas y aprobadas en pantalla el mismo día — ver §8/§9.
