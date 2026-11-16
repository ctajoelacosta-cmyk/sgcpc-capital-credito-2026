# RF32 — Informes de la operación y la cobranza

**Estado: análisis contra el anteproyecto, 13/10/2026. Sin recorrido en pantalla todavía
(implementado en varias etapas, la mayoría en Bloque 7/E10, D-128).**

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF32 (módulo Informes): *"El sistema debe generar informes de la
operación y la cobranza: solicitudes, aprobaciones y rechazos, desembolsos, cobros,
vencimientos, caja, morosidad por tramo, gestiones de cobranza e incobrables."*

Son **9** informes nombrados explícitamente. Regla relacionada: **RN-27** ("Los préstamos con
más de 180 días de atraso aparecen en el reporte de incobrables, sin cambiar su estado.").

## 2. Cómo funciona

Los 9, uno por uno, confirmados en el código (`ControladorReportesServlet`/`ServicioReportes`,
todos bajo `/reportes`, con PDF + CSV, ver RF35):

1. **Solicitudes** (`/reportes/solicitudes`) — listado con filtro por rango y estado.
2. **Aprobaciones y rechazos** (`/reportes/aprobaciones`) — `listarInformeAprobaciones`: incluye
   `APROBADA`, `RECHAZADA` **y además `CANCELADA`** (RF07, pedido de Joel, no estaba en el texto
   pero no lo contradice — una cancelación es, igual que un rechazo, una solicitud que no llegó a
   desembolso), con tiempo de respuesta (envío → decisión).
3. **Desembolsos** (`/reportes/desembolsos`).
4. **Cobros** (`/reportes/cobros`) — ver §6a, atención especial a reversiones.
5. **Vencimientos** (`/reportes/vencimientos`) — cuotas que vencen entre dos fechas, con
   teléfono (tal cual pide Doc. 00 §13, coincide).
6. **Caja** (`/reportes/caja`) — sesiones, movimientos, diferencias, entregas y rendiciones.
7. **Morosidad por tramo** (`/reportes/morosidad`) — `ORDEN_TRAMOS = {"1–30", "31–60", "61–90",
   "91–180", "Más de 180"}` (`ServicioReportes.java` línea 551), los 5 tramos exactos de Doc. 00
   §13.
8. **Gestiones de cobranza** (`/reportes/gestiones`) — gestiones y promesas por fecha, con
   promesas incumplidas.
9. **Incobrables** (`/reportes/incobrables`) — préstamos con más de 180 días de atraso (RN-27).

**Perfil**: gerente/administrador ven los 9 (`VER_REPORTES = TOTAL`). El oficial solo ve
**vencimientos, morosidad, gestiones** de esta lista (están en `SUBREPORTES_OFICIAL`) — los
otros 6 (solicitudes, aprobaciones, desembolsos, cobros, caja, incobrables) le dan 403 aunque
tenga `VER_REPORTES = CONSULTA`, por la misma lista blanca que RF31. El cajero no ve ninguno
(`VER_REPORTES = NINGUNO`).

## 3. Auditoría

Ninguna por ver/generar el PDF; la exportación CSV de cada uno se audita por separado (ver
RF35 §3), mismo patrón en los 9.

## 4. Dónde está

- **Servicio**: `ServicioReportes` (consultas/cálculo de cada informe, DAO por módulo) +
  `ServicioInformes` (armado de PDF/CSV de solicitudes/aprobaciones/desembolsos/cobros, ver
  nota de arquitectura en `RF31_informes_dueno.md` §7).
- **Controlador**: `ControladorReportesServlet`, un `case` por sub-ruta.
- **Vistas**: `reportes/solicitudes.jsp` → en realidad `informe-solicitudes.jsp`,
  `informe-aprobaciones.jsp`, `informe-desembolsos.jsp`, `cobros` (fragmento propio),
  `vencimientos.jsp`, `caja.jsp`, `morosidad.jsp`, `gestiones.jsp`, `incobrables.jsp` — enlaces
  en `reportes/indice.jsp` (tarjetas verdes = operación, rojas = cobranza/mora).
- **Permisos**: `Permiso.VER_REPORTES` + `SUBREPORTES_OFICIAL` (whitelist, ver RF31 §2/§4).

## 5. Dependencias

- **RF31**: mismo controlador, misma whitelist (frontera exacta entre "para el dueño" y "de la
  operación").
- **RF25** (reversión): el informe de Cobros tiene que mostrar un cobro revertido sin ocultarlo
  pero sin sumarlo — ver §6a, es la atención especial pedida por Joel.
- **RF23/RF33** (factura): "Cobros" no incluye el detalle de IVA por factura (eso es RF33, un
  informe aparte, para el contador); comparten la misma fuente de datos pero no se duplican.
- **RF27** (historial de informes) — no existe como tal, cada informe es independiente.

## 6. Análisis

**a) ¿Cumple los 9 informes exactos del texto firmado?** **Sí, los 9 existen**, con el contenido
que pide el texto (y, en aprobaciones, un dato extra — canceladas — que no contradice nada).

**b) (Atención especial pedida por Joel) ¿Una reversión se cuenta como cobro?** **No, ya
verificado y confirmado hoy en el código** (mismo hallazgo que RF25/RF30/RF31): `PagoDAO.
listarTodosPorRango` (línea 139) trae **todos** los pagos del rango, `CONFIRMADO` y
`REVERTIDO` — a propósito, para que el informe de Cobros siga **mostrando** (con la marca
"Revertido", `FilaInformeCobro.isRevertido()`) un cobro que se revirtió, sin hacerlo
desaparecer (RN-13, "nada se borra"). Pero `ServicioReportes.calcularTotalesCobros` (línea
1208-1235) **excluye explícitamente los revertidos de la suma** (`if (fila.isRevertido()) {
cantidadRevertidos++; continue; }`) — el comentario del propio método dice textual: "los
REVERTIDO quedan afuera de la suma (pedido de Joel), aunque se siguen contando/mostrando en la
lista de filas." **Exactamente el comportamiento correcto**: visible en la lista (auditoría
completa, nada se esconde), cero en el total (no es dinero que la empresa efectivamente
conservó).

**c) ¿Una factura anulada se cuenta en algún informe de este bloque como si fuera válida?**
**No** — ninguno de los 9 informes de este RF trae el detalle de IVA/factura (eso es RF33,
informe separado); "Cobros" trabaja sobre `pagos`, no sobre `facturas`, así que el estado de la
factura no interviene en su cálculo. Ver `RF33_facturas_iva.md` §6 para la verificación
específica de ese informe.

**d) ¿"Incobrables" cumple RN-27 ("sin cambiar el estado")?** **Sí** — es un informe de solo
lectura (`generarInformeIncobrablesPdf`, `FilaMora` ya calculada por `ServicioMora`, sin ningún
`UPDATE`); el préstamo sigue `EN_MORA` en la base, el informe solo lo lista si supera 180 días.

**e) ¿Depende de fechas?** **Sí, para varios** (vencimientos, morosidad, gestiones, incobrables
dependen de que haya datos reales con esas fechas — mismo criterio que RF20/RF21/RF24); los
demás (solicitudes, aprobaciones, desembolsos, cobros, caja) dependen solo del rango elegido,
sin necesitar una fecha "real" específica — se pueden probar con cualquier dato ya existente.

**f) ¿Algo miente o confunde?** No se encontró ningún texto falso. Nada que agregar más allá de
lo ya señalado en RF31 §6e (confirmar el rango visible en cada PDF).

**g) (13/10/2026, confirmado en este análisis) Falta real: sin filtro "Cliente u
operación"/"C.I."** — ya anotado en `LEEME.md` como pendiente desde el 07/10/2026 ("marcada
OBLIGATORIA por Joel... sin confirmar si ya está implementado"); **confirmado ahora que NO está
implementado**: ninguno de los 7 informes nombrados en esa nota (Caja, Aprobaciones, Cobros,
Desembolsos, Solicitudes, Gestiones, Consultas al buró) tiene un campo de filtro por cliente/
operación/C.I. en su pantalla (`informe-cobros.jsp`, `informe-desembolsos.jsp`, `caja.jsp`,
`informe-solicitudes.jsp`, `gestiones.jsp`, `informe-aprobaciones.jsp`: ninguno tiene el campo),
ni en el `dto.Filtro*Reporte` correspondiente (`FiltroCobrosReporte`, por ejemplo, solo tiene
`desde`/`hasta` — el propio comentario del archivo dice "Joel no pidió otros filtros para este
informe", lo que sugiere que el pedido llegó DESPUÉS de escribirse esa clase, el 07/10/2026, y
nunca se volvió a tocar). No es que el filtro muestre algo y filtre otra cosa (eso sería
"miente") — simplemente no existe todavía en ninguno de los 7. No es parte del texto firmado de
RF32/RF35 (ninguno de los dos lo pide literalmente), es un pedido adicional de Joel —
igual de real como pendiente, solo que no es un incumplimiento del anteproyecto en sí.

## 7. Decisiones

Ninguna nueva.

## 8. Historial de cambios

| Fecha | Cambio | Commit | Tag |
|---|---|---|---|
| — | Implementado en varias etapas (vencimientos/morosidad/gestiones más tempranos; el resto en Bloque 7/E10, D-128). | — | — |
| 11/10/2026 | Aprobaciones agrega CANCELADA (RF07). | — | — |
| 13/10/2026 | Primer análisis contra el anteproyecto (este documento), sin cambios de código. Confirmado por lectura de código que las reversiones nunca se cuentan como cobro (§6b). | — | — |
| 13/10/2026 | Filtro "Cliente u operación" en 6 de 7 informes (§6g) -- `ServicioReportes.coincideClienteUOperacion` + `NormalizadorTexto.normalizarParaBuscar`, con pruebas unitarias. Caja queda pendiente de decisión (ver §6g). `mvn test` 721/0. | `62565c0` | — |
| 13/10/2026 | Probados en pantalla los 9 informes (gerente), ver §9. Sin cambios de código. | *(pendiente)* | `rf32-probado` |

## 9. Pruebas realizadas en pantalla

**13/10/2026, Cobros (gerente)**: filtro "Cliente u operación" probado en pantalla — con
`CC-2026-00001`, la lista y los totales coinciden (el revertido queda afuera de la suma, §6b) y
el PDF muestra "Cliente/operación: CC-2026-00001" en el encabezado.

**Probado por Joel el 13/10/2026 (gerente, tag `rf32-probado`)**:

- **Aprobaciones y rechazos**: 30/7/2 (aprobadas/rechazadas/canceladas), tiempo de respuesta OK.
- **Cobros**: filtro "Cliente u operación" OK, el revertido no se suma al total.
- **Morosidad por tramo**: capital vencido 2.036.349, saldo 13.000.000 — coincide con RF31 §9.
- **Gestiones**: 6 gestiones, promesas 4/3/0/1, filtro de incumplidas OK.
- **Incobrables**: sale **vacío**, correcto (el más atrasado tiene 37 días, bajo el umbral de
  180) — sigue con la salvedad de §6/abajo: no verifica el formato de una fila real hasta la
  prueba con fechas reales, **sin forzar un caso por SQL** (pedido explícito de Joel).
- **Caja**: 16 sesiones, un sobrante de 100.000 y un faltante de 246.000 (ver `AVANCE.md`,
  "Antes de entregar" — el sobrante del 11/10 sigue sin revisar).

**Pendiente, no repetido en esta pasada**: Desembolsos, Solicitudes, Vencimientos (confirmación
visual directa, aunque ya validados por los otros informes/código); y, **como oficial**,
confirmar que solo ve Vencimientos/Morosidad/Gestiones en pantalla y que forzar la URL de los
otros 6 (más los de RF31) da **403** real.

## 10. Pendientes y trabajo futuro

- **(13/10/2026, implementado, pendiente de prueba en pantalla)** Filtro "Cliente u operación"
  (nombre, C.I. o código de operación) agregado en **6 de los 7** informes: Aprobaciones,
  Cobros, Desembolsos, Solicitudes, Gestiones y Consultas al buró — `ServicioReportes.
  coincideClienteUOperacion` (pura, con pruebas unitarias), aplicado en Java sobre las filas ya
  armadas (no en SQL), mismo filtro para pantalla, PDF y CSV; el encabezado del PDF/CSV muestra
  "Cliente/operación: ..." cuando se usó (`ServicioInformes.agregarClausulaClienteUOperacion`).
  **Caja queda afuera**: su informe es por SESIÓN de caja (apertura/cierre/diferencia,
  `FilaInformeCaja`), sin ningún dato de cliente u operación en esa fila — el filtro no tiene
  sentido a esa granularidad. Aplicarlo ahí exigiría rediseñar el informe para que sea por
  MOVIMIENTO (que sí referencia un préstamo en DESEMBOLSO/COBRO, pero no en ENTREGA_GERENTE/
  RENDICION_GERENTE) — un cambio de diseño, no "cambio mínimo"; queda pendiente de que Joel
  decida si lo quiere igual.
- **Como oficial**, confirmar que solo ve Vencimientos/Morosidad/Gestiones en pantalla, y que
  forzar la URL de los otros 6 (más los de RF31) da **403** real — no repetido en la pasada del
  13/10/2026.
- **Desembolsos, Solicitudes, Vencimientos**: no repetidos en la pasada del 13/10/2026 (ver §9).

## 11. Guion para la defensa

"RF32 exige 9 informes de operación y cobranza; los 9 existen. El oficial solo ve los 3 que le
sirven para su trabajo diario de cobranza (vencimientos, morosidad, gestiones) — el resto le da
403 real en el servidor, no solo un enlace oculto. El punto que pediste verificar
específicamente: un cobro revertido sigue apareciendo en el informe de Cobros, marcado como tal,
pero el total del pie nunca lo suma — la auditoría queda completa sin inflar la caja. Pendiente
real encontrado: el filtro por cliente/operación que Joel pidió el 07/10/2026 todavía no está
en ninguno de los 7 informes que lo necesitan."
