# RF20 — Buscar préstamo y lista de trabajo de cobranza

**Estado: probado contra el anteproyecto, completo el 12/10/2026 (`rf20-probado`).** Decisiones
de Joel: 1) la lista de trabajo completa (3 tarjetas + búsqueda) queda para la cajera y el
administrador; el oficial sigue con `/mora` (RF24) — queda anotado como mejora no exigida (§10)
que el oficial vea "Próximos 7 días" para llamar antes del vencimiento. 2) un préstamo `EN_MORA`
no se repite en "Vencen hoy" ni en "Próximos 7 días" — implementado en `ServicioCobranza.
armarListaTrabajo` (§6/§7), verificado con pruebas unitarias; el hallazgo de §6b (préstamo
`ANULADO_ANTES_DESEMBOLSO` en la búsqueda) también implementado y confirmado en pantalla.
**Único punto que sigue pendiente**: la prueba con datos reales en las 3 tarjetas (vencido/vence
hoy/próximos 7 días) y la confirmación en pantalla de la Decisión 2 con un caso real — diferida a
la prueba con el paso real del tiempo, decisión de Joel de no simular fechas ahora (ver §9/§10).

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF20 (módulo Cobranza y mora): *"El sistema debe permitir buscar un
préstamo por cliente, cédula o número de operación, y mostrar una lista de trabajo con los
préstamos que vencen hoy, los que están en mora y los que vencen en los próximos 7 días."*

Perfiles según el anteproyecto (`docs/00_especificacion_maestra.md`): la cajera cobra las cuotas;
el oficial de crédito "registra gestiones de cobranza a morosos" (RF24, pantalla separada).

## 2. Cómo funciona

Implementado en E7, con una vuelta de mejoras D-146 (pedido de Joel) que lo deja más completo que
el texto mínimo del RF. Todo vive en `GET /cobranza` (`ControladorCobranzaServlet.mostrarBusqueda`):

- **Sin texto de búsqueda** (entrada normal a la pantalla): muestra la lista de trabajo en 3
  tarjetas, en este orden — "En mora" (roja), "Vencen hoy" (amarilla), "Próximos 7 días" (gris) —
  cada una con cliente, C.I., operación, cuota, monto y botón "Cobrar". Si una tarjeta no tiene
  nada, dice "Ningún préstamo en mora."/"Nada vence hoy."/"Nada vence en los próximos 7 días."
- **Con texto de búsqueda** (`GET /cobranza?q=...`, búsqueda en vivo por AJAX, un solo campo):
  prueba primero el número de operación exacto; si no, busca por cédula (con o sin puntos),
  nombre o apellido entre los clientes con préstamo `ACTIVO`/`EN_MORA`. Distingue varios casos
  (ver §6) con un mensaje claro en cada uno, nunca un "sin resultados" genérico sin motivo.

## 3. Auditoría

Ninguna: `/cobranza` (la búsqueda y la lista de trabajo) es una consulta, no registra nada en
`auditoria_operaciones`. (El cobro en sí, al confirmarlo desde la ficha, sí audita — eso es
RF22, fuera de este documento.)

## 4. Dónde está

- **Controlador**: `ControladorFormalizacionServlet` no — es `ControladorCobranzaServlet.
  mostrarBusqueda` (`/cobranza`, `GET`).
- **Servicios**: `ServicioCobranza.buscarParaCobranza` (búsqueda, con `resolverUnPrestamo`/
  `resolverSinPrestamoActivo`/`calcularSituacionCliente`/`construirTextoAnulado`); `ServicioCobranza.
  armarListaTrabajo` (Decisión 2, pura — recibe las 3 listas ya traídas por el controlador, no las
  busca ella misma: ver §7 por qué); `ServicioMora.listarPrestamosEnMora` (tarjeta "En mora",
  reusada tal cual de RF24); `ServicioReportes.listarVencimientos` (tarjetas "Vencen hoy"/
  "Próximos 7 días", reusada tal cual del informe de Vencimientos, RF32); `ServicioAutorizacion.
  tienePermiso` (decide el enlace a Formalización del hallazgo de §6b).
- **DAO**: `PrestamoDAO.obtenerPorIdentificadorOperacion`, `.listarActivosOEnMora`,
  `.listarTodosPorCedula`; `PersonaDAO.construirWhere` (filtro de texto, `ILIKE` sin acentos).
- **Vistas**: `cobranza/busqueda.jsp` (formulario), `cobranza/_resultados_busqueda.jsp`
  (fragmento de resultados, mismo mecanismo de "listado vivo" que `/clientes`, D-136).
- **Permisos**: `FiltroAutorizacion`, ruta `/cobranza` → `Permiso.COBRAR` (cajera TOTAL,
  administrador TOTAL, gerente y oficial NINGUNO — `ServicioAutorizacion.construirMatriz`).
- **Menú**: `plantilla/menu.jsp`, entrada "Cobranza" habilitada solo para `esCajero ||
  esAdministrador` (candado para los demás perfiles, mismo patrón que el resto del sistema).

## 5. Dependencias

- **RF21-RF23** (deuda exigible, cobro, recibo/factura): la ficha a la que lleva el botón
  "Cobrar" de cada tarjeta/resultado.
- **RF24** (gestiones y promesas de pago, `/mora`): mismo dato de "en mora"
  (`ServicioMora.listarPrestamosEnMora`), pantalla separada para el oficial.
- **RF32** (informe de Vencimientos): usa el mismo `ServicioReportes.listarVencimientos`/
  `calcularTotalesVencimientos` que las tarjetas "Vencen hoy"/"Próximos 7 días" — cualquier
  cambio ahí afecta a los dos; por eso la Decisión 2 (§7) no toca ese método compartido.
- **RF14** (anular antes del desembolso): mismo tipo de omisión ya encontrada ahí (la ficha de
  desembolso de un anulado no lo dice explícitamente) se repite acá en la búsqueda (§6/§10).

## 6. Análisis (13/10/2026, pedido de Joel — a, b, c, d, e)

**a) Permisos en el servidor, oficial o gerente entrando a `/cobranza` por la dirección**:
`FiltroAutorizacion` resuelve `Permiso.COBRAR` para esa ruta; en la matriz
(`ServicioAutorizacion.construirMatriz`) gerente y oficial tienen `NivelAcceso.NINGUNO` ahí (solo
cajera y administrador tienen `TOTAL`). El filtro responde `403` (`sendError`, "No tiene permiso
para esta acción.") y Tomcat muestra `error-403.jsp`: "Acceso restringido — No tiene permiso para
acceder a esta sección." con un botón "Volver al panel". En el menú ya no llegan a intentarlo sin
querer: "Cobranza" aparece con candado para esos dos perfiles (no es un enlace).

**b) La búsqueda — casos probados leyendo el código** (`ServicioCobranza.buscarParaCobranza` y
`PersonaDAO.construirWhere`):
- **Texto de 1-2 letras**: no hay mínimo de caracteres. Entra por el filtro de clientes
  (`cedula_identidad LIKE '%texto%' OR nombres ILIKE '%texto%' OR apellidos ILIKE '%texto%'`, sin
  acentos). Con una letra puede traer varios clientes; si ninguno tiene préstamo cobrable, se
  listan hasta `MAXIMO_COINCIDENCIAS_AMBIGUAS` = 10 coincidencias (nombre, C.I., situación), con
  "Hay más coincidencias que no se muestran. Refiná la búsqueda." si hay más de 10. No se cae, no
  da error — solo puede ser una lista larga de revisar.
- **Caracteres especiales**: sin riesgo de inyección (SQL siempre parametrizado, `setString`).
  El texto del usuario **no** se escapa para los caracteres propios de `LIKE` (`%`/`_`) —
  si alguien escribe literalmente un `%`, el patrón queda más amplio de lo esperado (ej. "%%%"
  en vez de buscar un `%` literal). Es un comportamiento **general** del buscador de clientes
  (lo mismo pasa hoy en `/clientes`), no algo nuevo de Cobranza — lo anoto en §10 por prolijidad,
  no es un hallazgo de RF20 en particular.
- **Cédula que no existe**: no matchea ni por cédula ni por nombre/apellido →
  `"No existe un cliente con esos datos."` (texto exacto).
- **Préstamo `ANULADO_ANTES_DESEMBOLSO` (ej. CC-2026-00002, Rodrigo) — HALLAZGO, CORREGIDO**:
  buscando el número de operación exacto, `resolverUnPrestamo` encuentra el préstamo, ve que no
  es `ACTIVO`/`EN_MORA`/`PENDIENTE_DESEMBOLSO` y lo trata igual que "sin préstamo activo"
  (`resolverSinPrestamoActivo` → `calcularSituacionCliente`). Ese método antes **solo**
  distinguía dos casos especiales — `PENDIENTE_DESEMBOLSO` ("tiene el préstamo X pendiente de
  desembolso") y `FINALIZADO` ("no tiene préstamos activos. Último: X cancelado el...") —
  `ANULADO_ANTES_DESEMBOLSO` no tenía caso propio y caía al genérico. **Agregado un tercer caso**:
  "tiene la operación CC-2026-00002, anulada antes del desembolso por [nombre] el [fecha y
  hora]. Motivo: [motivo]. No tiene préstamos activos." — mismo criterio que
  `ServicioDesembolso.construirMensajePrestamoYaAnulado` para resolver quién ("otro usuario" si
  no hay dato). Enlace a la ficha de Formalización SOLO si el perfil tiene permiso de verla
  (`Permiso.GENERAR_DOCUMENTOS_FORMALIZACION` — la cajera no, el administrador sí): sin permiso,
  el mensaje sale igual pero sin ningún enlace.

**c) Cómo se calculan "hoy" y "próximos 7 días"**: `Reloj.hoy()` usa `ZoneId.of("America/
Asuncion")` explícito (no depende de la zona del servidor ni de `TimeZone.setDefault`).
`ServicioReportes.listarVencimientos(conexion, desde, hasta)` filtra por
`cuota.getFechaVencimiento()` — la fecha **ya corrida** a día hábil siguiente si el vencimiento
teórico caía feriado/fin de semana (la calcula `ServicioMotorFinanciero.generarPlanPagos` al
generar el plan, guardada aparte de `fechaVencimientoTeorica`). Por eso un vencimiento de sábado
corrido al lunes aparece correctamente el lunes (en "Vencen hoy" ese día, o en el rango de
"Próximos 7 días" si cae dentro de la ventana) — no hay ningún cálculo de fecha propio en esta
pantalla ni en el controlador, todo reusa el mismo método que ya usa el informe de Vencimientos
(RF32).

**d) Qué se puede probar HOY (12/10/2026) sin cuotas vencidas**: con Mirtha CC-2026-00001 (ACTIVO,
primera cuota 24/11/2026, a 43 días — fuera de cualquier ventana de "próximos 7 días") y Rodrigo
CC-2026-00002 (`ANULADO_ANTES_DESEMBOLSO`, sin otro préstamo), hoy se puede probar en pantalla:
- Las 3 tarjetas de la lista de trabajo, **vacías** (sus 3 textos de "nada" exactos, de arriba).
- La búsqueda de Mirtha (por cédula, nombre o CC-2026-00001): aparece con su próxima cuota y
  monto a cobrar, botón "Cobrar".
- La búsqueda de Rodrigo (ya corregida — ver el mensaje nuevo de (b)).
- Los permisos de (a): oficial/gerente con 403, candado en el menú.
Lo que queda para la prueba con el paso real del tiempo (decisión de Joel, no se simula ahora):
que las 3 tarjetas muestren algo real — un préstamo vencido hoy, uno en mora, uno dentro de los
próximos 7 días — y que ese caso confirme en pantalla la Decisión 2 (que uno en mora no se
repita en las otras dos), ya implementada pero sin dato real para probarla hoy.

**e) Cumplimiento contra el texto firmado**: el RF exige buscar por cliente, cédula o número de
operación — **cumple**, y además distingue con mensajes claros todos los casos que el texto no
detalla (pendiente de desembolso, ambiguo, sin préstamos, anulado antes del desembolso). Exige
mostrar vencen-hoy/en-mora/próximos-7-días, sin repetir un préstamo entre tarjetas — **cumple**,
con el nombre exacto de cada categoría y la Decisión 2 ya programada. Queda pendiente solo la
reprueba en pantalla con datos reales de las 3 tarjetas (§9/§10) antes de decir "probado contra
el anteproyecto" sin reservas.

## 7. Decisiones

- **(13/10/2026, Joel) Decisión 1 — alcance por perfil**: la lista de trabajo completa (3
  tarjetas + búsqueda) queda para la cajera y el administrador, como está hoy. El oficial sigue
  con `/mora` (RF24) para su seguimiento de mora. Que el oficial vea también "Próximos 7 días"
  (para llamar antes del vencimiento) queda anotado como mejora **no exigida** por el RF — ver
  §10, sin programar.
- **(13/10/2026, Joel) Decisión 2 — sin duplicado en mora, IMPLEMENTADA con un ajuste de
  diseño**: un préstamo `EN_MORA` no aparece más en "Vencen hoy" ni en "Próximos 7 días". Joel
  pidió la regla "en un método de `ServicioCobranza`" — al programarlo apareció un **ciclo de
  construcción**: `ServicioMora` y `ServicioReportes` YA tienen cada uno un campo
  `new ServicioCobranza()` (para su propia lógica, de antes de este RF); si `ServicioCobranza`
  también tuviera un campo `new ServicioMora()`/`new ServicioReportes()`, instanciar cualquiera
  de los tres entra en recursión infinita — confirmado corriendo `mvn test`: **131 pruebas**
  (todo `ServicioCobranzaTest` y `ServicioReportesTest`) fallaron con `StackOverflowError` en el
  primer intento. Mismo problema, y misma solución, que ya documentó
  `ServicioMotorFinanciero.sumarCuotasOtrasDeudas` (RF11) para un ciclo parecido entre
  `ServicioSolicitudes`/`ServicioFormalizacion`. **Diseño final**: `ServicioCobranza.
  armarListaTrabajo(List<FilaMora> enMora, List<FilaVencimiento> vencenHoy, List<FilaVencimiento>
  proximos7Dias)` es **pura** (sin `Connection`, sin campo a `ServicioMora`/`ServicioReportes`) —
  recibe las 3 listas ya traídas por `ControladorCobranzaServlet.mostrarBusqueda` (que sigue
  llamando a `servicioMora.listarPrestamosEnMora`/`servicioReportes.listarVencimientos`
  directamente, igual que antes) y devuelve un `ListaTrabajoCobranza` (DTO nuevo) con "vencenHoy"/
  "proximos7Dias" ya sin los préstamos de "enMora". La REGLA de negocio (qué se excluye y por
  qué) queda en el servicio, cumpliendo REGLAS_DESARROLLO.md §13 punto 4 — lo que quedó en el
  controlador es solo la orquestación de a qué servicios llamar, que ya hacía antes de este RF.
  No se tocó `ServicioReportes.listarVencimientos` ni `calcularTotalesVencimientos` — el informe
  de Vencimientos (RF32) sigue mostrando todo, sin excluir nada.
- **(13/10/2026, Joel) Hallazgo de §6b — IMPLEMENTADO**: `calcularSituacionCliente` ahora
  distingue también `ANULADO_ANTES_DESEMBOLSO` (ver §6b para el mensaje exacto y el enlace
  condicionado al permiso).

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 15/11/2026 | **B197** (tanda 2): el bloque *"En mora"* de la lista de trabajo pagina de a 20 — es el único grande de los tres (69 filas contra 1 y 0) — y su píldora cuenta el **total**, no las filas de la página. Esta pantalla tiene una sola paginación, así que usa el parámetro `pagina` de siempre y pasa por `listado-vivo.js`, igual que Clientes o Solicitudes. `mvn test` 1033/0. Estado y detalle, en `FASE2_PLAN.md`. | *(este commit)* | — |
| 15/11/2026 | **B193 + B196**. **B193**: la lista de trabajo avisa que no hay caja abierta (o que la abierta es de otro día), con el mismo texto, color y enlace que ya tenía la ficha — antes ofrecía un botón *"Cobrar"* por fila y el bloqueo aparecía un clic después. Es copiar **B186** de la bandeja de desembolso, y el aviso vive en `busqueda.jsp` (fuera del fragmento de resultados) porque no depende de la búsqueda. **B196**: un bloque vacío ya no dibuja su tarjeta —antes alcanzaba con que la lista no fuera null, y quedaba el encabezado de color con los títulos de columna y nada debajo— y cuando los **tres** están vacíos aparece una sola línea en verde que lo dice y que explica qué hacer (buscar el préstamo por cédula, nombre o N.º de operación). `mvn test` 1022/0. Estado y detalle, en `FASE2_PLAN.md`. | *(este commit)* | — |
| 12/10/2026 | Reprueba en pantalla de Joel: VERIFICADO (cajera con las 3 tarjetas vacías y sus textos exactos, menú con candado en "Seguimiento de mora"/"Reversiones"; búsqueda "mirtha"/"9.100.100" → CC-2026-00001 al día con botón Cobrar; búsqueda "CC-2026-00002" → mensaje nuevo del anulado, sin botón para la cajera; búsqueda "1234567" → "No existe un cliente con esos datos."; oficial en `/cobranza` → "Acceso restringido", candado en el menú). Cierre de RF20. | *(pendiente)* | `rf20-probado` |
| 13/10/2026 | Programado lo aprobado: `ServicioCobranza.armarListaTrabajo` (Decisión 2, pura, con el ajuste de diseño por el ciclo de construcción) y el caso `ANULADO_ANTES_DESEMBOLSO` en `calcularSituacionCliente`/`construirTextoAnulado` (hallazgo §6b), con enlace a Formalización condicionado al permiso. DTO nuevo `ListaTrabajoCobranza`; `ResultadoBusquedaCobranza`/`CoincidenciaBusquedaCobranza` con el campo `enlaceAFormalizacion`. `mvn test`: 692 en verde (6 pruebas nuevas). | — | — |
| 13/10/2026 | Análisis contra el anteproyecto (este documento). Sin cambios de código. | — | — |
| (E7, sin fecha exacta) | Implementación base: búsqueda por operación/cédula/nombre, lista de trabajo en 3 tarjetas | — | — |
| D-146 (sin fecha exacta, pedido de Joel) | Búsqueda en vivo (AJAX), mensajes claros por caso (pendiente de desembolso, ambiguo, sin préstamos), reordenado "En mora" primero | — | — |

| 30/10/2026 | **B82**: la búsqueda de Cobranza acepta **parte del número de operación** (`00005` encuentra `CC-2026-00005`), que es lo que `/prestamos` ya hacía desde B1. La regla tiene **un solo dueño**, `ServicioCobranza.buscarPrestamosPorNumeroOperacion`: **exacto primero, sin importar mayúsculas; si no hay, fragmento** (`PrestamoDAO.buscarPorFragmentoIdentificadorOperacion`, el mismo `ILIKE` del DAO) con tope de 10 coincidencias, porque un fragmento corto como `2026` entra en todos los préstamos. Con un solo resultado se sigue usando `resolverUnPrestamo`, así que no se perdió ninguno de los diagnósticos de D-139/D-146 ("pendiente de desembolso", "ya finalizado", cliente sin préstamos); con varios se ofrecen los cobrables y, si ninguno lo es, se pide el número completo. Ese mismo dueño lo usa ahora el Estado de cuenta (**B93**, [RF34_estado_cuenta.md](RF34_estado_cuenta.md)). **Probado en pantalla el mismo día** (§9). `mvn test` 858/858 | `9b66988` | — |

## 9. Pruebas realizadas en pantalla

**12/10/2026 (Joel) — VERIFICADO EN PANTALLA**:
- Cajera: las 3 tarjetas de la lista de trabajo vacías, con sus textos exactos ("Ningún préstamo
  en mora." / "Nada vence hoy." / "Nada vence en los próximos 7 días."); en el menú,
  "Seguimiento de mora" y "Reversiones" con candado (sin acceso).
- Búsqueda "mirtha" y "9.100.100" → CC-2026-00001, próxima cuota 1/12, vence 24/11/2026,
  483.199 Gs., "Al día", botón "Cobrar".
- Búsqueda "CC-2026-00002" → "…tiene la operación CC-2026-00002, anulada antes del desembolso
  por Joel Jesús Acosta el 12/10/2026 00:11. Motivo: … No tiene préstamos activos." **sin
  botón** (la cajera no tiene permiso de Formalización).
- Búsqueda "1234567" → "No existe un cliente con esos datos."
- Oficial (Lorenzo Ramírez) en `/cobranza` por la dirección → "Acceso restringido"; "Cobranza"
  con candado en el menú.

**Pendiente, diferido a la prueba con fechas reales (decisión de Joel, al final)**: las 3
tarjetas con préstamos reales, y que un préstamo `EN_MORA` aparezca solo en "En mora" — hoy
cubierto por las pruebas unitarias (§6/§7), sin un caso real todavía para confirmarlo en pantalla.

## 10. Pendientes y trabajo futuro

- **(13/10/2026) Mejora no exigida por el RF (Decisión 1, Joel)**: que el oficial vea "Próximos
  7 días" en `/mora` para poder llamar antes del vencimiento, no solo lo ya vencido. No
  programado — RF20 no lo exige para el oficial (esa pantalla es de RF24).
- **Quirk general del buscador de clientes** (no específico de RF20, ver §6b): el texto de
  búsqueda no escapa `%`/`_` antes de armar el `LIKE`. Comparte el mismo comportamiento que
  `/clientes`. No se propone tocarlo acá (fuera del alcance de este RF).
- **(12/10/2026, Joel) Prueba con datos reales en las 3 tarjetas** (vencido, vence hoy, próximos
  7 días, y que la Decisión 2 realmente evite el duplicado con un caso real en pantalla): diferida
  a la prueba con el paso real del tiempo, decisión de Joel de no simular fechas ahora. Hoy
  cubierto solo por pruebas unitarias.

## 11. Guion para la defensa

1. Como cajera, entrar a "Cobranza y mora → Cobranza" — mostrar las 3 tarjetas (hoy, vacías).
2. Buscar "CC-2026-00001" (o "Mirtha") — mostrar el resultado con la próxima cuota y el botón
   "Cobrar".
3. Buscar una cédula inexistente — mostrar "No existe un cliente con esos datos."
4. Buscar "CC-2026-00002" (Rodrigo) — mostrar el mensaje con quién anuló, cuándo y el motivo; sin
   botón para la cajera, con "Ver ficha de formalización" para el administrador.
5. Como oficial o gerente, entrar a `/cobranza` por la dirección — mostrar "Acceso restringido".
6. (Cuando haya datos con el tiempo real) mostrar un préstamo en cada una de las 3 tarjetas, sin
   que el mismo préstamo se repita entre "En mora" y las otras dos (Decisión 2, ya programada).
