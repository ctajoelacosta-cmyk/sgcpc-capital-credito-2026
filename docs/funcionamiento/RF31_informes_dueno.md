# RF31 — Informes para el dueño (financiero, mensual, cartera, proyección)

**Estado: probado en pantalla el 13/10/2026 (tag `rf31-probado`).** Resumen financiero y
mensual (totales cuadran), proyección 30/60/90 y cartera, verificados contra los datos reales de
`sgcpc_db_pruebas` (implementado en varias etapas, el resumen financiero/proyección en Bloque
7/E10, D-128). Incluye la corrección de la etiqueta de morosidad por capital vencido (hallazgo de
Joel, ver §9).

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF31 (módulo Informes): *"El sistema debe generar informes para el
dueño: resumen financiero, resumen mensual, cartera y proyección de fondos a 30, 60 y 90 días."*

## 2. Cómo funciona

4 informes, todos bajo `/reportes` (`ControladorReportesServlet`), cada uno con PDF
(`GeneradorInformesPdf`/JasperReports) y CSV (`ExportadorCsv`) — ver RF35 para el mecanismo
transversal de filtro y exportación:

- **Resumen financiero** (`/reportes/resumen-financiero`, D-128): rango **desde-hasta
  arbitrario** (no solo un mes calendario) — créditos otorgados y en trámite, cobrado desglosado
  (capital/interés/moratorio/punitorio/IVA), ganancia bruta, cartera, % de mora, demanda por
  tramo de monto y de plazo.
- **Resumen mensual** (`/reportes/resumen-mensual`): un mes calendario (`YearMonth`) —
  préstamos otorgados (cantidad y monto), cobrado (capital/interés/mora/IVA), ganancia
  (intereses + mora sin IVA), % de morosidad.
- **Cartera** (`/reportes/cartera`): préstamos activos a una fecha, capital en la calle,
  intereses por cobrar, con resumen por estado.
- **Proyección de fondos** (`/reportes/proyeccion-fondos`, D-128): a una fecha de corte,
  ingresos esperados (cuotas por vencer) y egresos comprometidos (aprobado sin desembolsar) a
  **30, 60 y 90 días** (`HORIZONTES_PROYECCION = {30, 60, 90}`, `ServicioReportes.java` línea
  1388) — los 3 horizontes exactos del texto firmado, ni uno more ni uno menos.

**Restringido al dueño de verdad**: `SUBREPORTES_OFICIAL` (línea 47-51 del controlador) es una
lista blanca de los únicos sub-informes que el oficial puede ver (`vencimientos`, `morosidad`,
`gestiones`, `estado-cuenta`, `consultas-buro`) — **ninguno de los 4 de este RF está en esa
lista**, así que una URL directa a `/reportes/resumen-financiero` (u otro) con sesión de oficial
da **403**, aunque `Permiso.VER_REPORTES` del oficial sea `CONSULTA` (no `NINGUNO`). El cajero
ni llega a `/reportes` (`VER_REPORTES = NINGUNO`, 403 en el filtro antes de llegar al
controlador).

## 3. Auditoría

Ninguna por generar/ver un PDF — solo la exportación a CSV se audita (`auditarExportacionCsv*`,
ver RF35 §3), mismo patrón que el resto de los informes.

## 4. Dónde está

- **Servicio**: `ServicioReportes` (cálculo de cada informe) + `ServicioInformes` (armado de PDF
  y texto de filtros) — dos clases distintas, ver nota en §7.
- **Controlador**: `ControladorReportesServlet`, casos `"resumen-mensual"` (línea 122),
  `"resumen-financiero"` (línea 176), `"proyeccion-fondos"` (línea 248), más el de `"cartera"`.
- **Vistas**: `reportes/resumen-financiero.jsp`, `reportes/resumen-mensual.jsp`,
  `reportes/cartera.jsp`, `reportes/proyeccion-fondos.jsp`; enlaces en `reportes/indice.jsp`
  (tarjetas azules).
- **Reportes Jasper**: generados vía `GeneradorInformesPdf`, no `.jrxml` propios por informe
  (confirmar en código si aplica un layout genérico de tabla — ver §10).
- **Permisos**: `Permiso.VER_REPORTES` (gerente/administrador `TOTAL`, oficial `CONSULTA`,
  cajero `NINGUNO`) + `SUBREPORTES_OFICIAL` (whitelist adicional, excluye estos 4).

## 5. Dependencias

- **RF32**: mismo controlador y mismo mecanismo (`SUBREPORTES_OFICIAL` es la frontera exacta
  entre "informes del dueño" (este RF) e "informes de la operación" (RF32, que el oficial sí ve
  parcialmente)).
- **RF35**: el filtro por rango y la exportación PDF/CSV son transversales a los 4 informes de
  este RF — ver ese documento para el mecanismo común.
- **RF23/RF33** (factura/IVA): "cobrado... IVA" del resumen financiero/mensual usa la misma
  fuente de IVA que RF33, nunca una cuenta aparte.

## 6. Análisis

**a) ¿Cumple los 4 informes exactos del texto firmado?** **Sí**, los 4 existen con esos nombres
y ese contenido. La proyección usa exactamente 30/60/90 días, sin agregar ni quitar horizontes.

**b) ¿"Para el dueño" se cumple con una restricción real, no solo de nombre?** **Sí** — la
whitelist `SUBREPORTES_OFICIAL` es una validación real en el servidor (403, no solo un enlace
oculto en la pantalla), confirmada leyendo el código. El oficial no puede llegar a ninguno de
estos 4 informes ni forzando la URL.

**c) ¿Las cifras de "cobrado"/"ganancia" excluyen reversiones y facturas anuladas (atención
especial pedida por Joel)?** **Sí, por el mismo patrón ya confirmado en RF30 §6c y en RF25
(hallazgo de hoy)**: toda consulta de `pagos` usada para estos totales filtra
`estado_pago = 'CONFIRMADO'` (ver `PagoDAO`, líneas 200/223/237, reutilizadas por
`ServicioReportes` para resumen mensual/financiero/cartera) — un pago `REVERTIDO` nunca entra en
"cobrado". El IVA del resumen usa la misma fuente que RF33 (facturas `EMITIDA`, nunca
`ANULADA` — ver `FacturaDAO` línea 90 vs. 127). **No encontré ningún camino donde una reversión
o una factura anulada se sume por error a estos informes.**

**d) ¿Depende de fechas?** El resumen financiero y la proyección dependen del rango/fecha de
corte que se elija (parámetro de la propia pantalla, no algo fijo) — se pueden probar con
cualquier rango que ya tenga datos reales, sin necesitar preparar fechas especiales (a
diferencia de RF20/RF21/RF24, que dependían de que una mora fuera "de verdad" al momento de la
prueba). El resumen mensual depende de que el mes elegido tenga datos — probar con el mes actual
alcanza.

**e) ¿Algo miente o confunde?** No se encontró ningún texto falso. Un matiz a confirmar en
pantalla (no es un defecto de código): verificar que el PDF de cada informe cite el rango/mes
elegido en el encabezado, para que no se confunda con "todo el histórico" si se mira sin el
filtro visible.

## 7. Decisiones

Ninguna nueva. Nota de arquitectura (no una decisión a tomar, solo una observación para la
defensa): el sistema tiene **dos** clases de servicio con nombres parecidos —
`ServicioReportes` (cálculo/consultas) y `ServicioInformes` (armado de PDF/CSV de otro grupo de
informes, principalmente clientes/préstamos/solicitudes/aprobaciones) — no es una duplicación
accidental, son dos familias de informes con historial de implementación distinto (D-128 vs.
etapas anteriores), pero conviene tenerlo claro para no buscar un método en la clase que no es.

## 8. Historial de cambios

| Fecha | Cambio | Commit | Tag |
|---|---|---|---|
| — | Implementado en Bloque 7/E10, Tanda D (D-128). | — | — |
| 13/10/2026 | Primer análisis contra el anteproyecto (este documento), sin cambios de código. | `b92f862` | — |
| 13/10/2026 | Probado en pantalla; corregida la etiqueta de morosidad por capital vencido (hallazgo de Joel, ver §9) en resumen financiero y mensual (pantalla, PDF y CSV). | `4c93e57` | `rf31-probado` |

## 9. Pruebas realizadas en pantalla

**Probado por Joel el 13/10/2026 (tag `rf31-probado`)**:

1. Resumen financiero y resumen mensual: totales cuadran (5.898.961 cobrado, 219.345 ganancia sin
   IVA, IVA aparte).
2. Proyección 30/60/90: flujo neto = ingresos − comprometidos; lo "en trámite" no se suma (§6).
3. Cartera: 21.842.321 = 8.842.321 activos + 13.000.000 en mora.
4. **Hallazgo de Joel, antes de taguear**: la etiqueta "Morosidad por monto de cartera: 9,32 %"
   no deja claro qué mide — con la cartera de arriba, los préstamos en mora suman 13.000.000 de
   21.842.321 (59,5 %), un número bien distinto del 9,32 % mostrado. Confirmado por lectura de
   código y por `SELECT` de solo lectura contra `sgcpc_db_pruebas` que el 9,32 % es **capital
   VENCIDO sobre la cartera** (`ServicioReportes.calcularResumenMensual`, acumulador
   `capitalVencido` ~líneas 288-297 y `setMorosidadPorcentajeMonto` ~líneas 311-313;
   `calcularResumenFinanciero`, mismo patrón ~líneas 887-925), no el saldo total de los préstamos
   en mora (2.036.349 / 21.842.321 = 9,32 % vs. 13.000.000 / 21.842.321 = 59,5 %). **Corregido**
   (cambio mínimo, pantalla + PDF + CSV, resumen financiero y mensual): la etiqueta pasa a decir
   "Por capital vencido sobre la cartera" y se agrega debajo "Saldo de préstamos en mora: 59,52 %"
   (nuevo campo `saldoPrestamosEnMoraPorcentaje` en `ResumenMensual`/`ResumenFinanciero`, calculado
   con `calcularSaldoCapital`, ya existente). Confirmado por Joel en pantalla y en el PDF del
   resumen mensual tras la corrección.
5. **Como oficial**, pendiente de forzar la URL de los 4 informes para confirmar el 403 — no
   repetido en esta pasada (ver §10).

Dos cosméticos anotados para Lote 2 (PA-25, `AVANCE.md`): el nuevo renglón "Saldo de préstamos en
mora" queda pegado al título siguiente en el PDF del resumen mensual (falta espacio), y "Filtros
aplicados: Resumen de 2026-10" debería decir "octubre de 2026".

## 10. Pendientes y trabajo futuro

- **Como oficial**, forzar la URL de los 4 informes para confirmar el 403 real (whitelist
  `SUBREPORTES_OFICIAL`, §2) — no repetido en la pasada del 13/10/2026.
- Confirmar visualmente que el rango/mes elegido se vea claro en el PDF de cada informe (§6e).
- Verificar si hay `.jrxml` propios para estos informes o si usan un layout genérico de tabla
  (`GeneradorInformesPdf`) — no confirmado con certeza en este análisis, solo por los nombres de
  método.
- PA-25 (Lote 2, cosmético): espacio entre "Saldo de préstamos en mora" y el título siguiente en
  el PDF mensual; "Resumen de 2026-10" → "octubre de 2026" en `FILTROS_APLICADOS`.
- **(13/10/2026, Tanda 2 — anotar, no implementar)**: PA-25 solo anotaba el PDF **mensual**, pero
  el mismo campo `saldoPrestamosEnMoraPorcentaje` se agregó a los dos informes (§9, línea 137) —
  `informe_resumen_financiero.jrxml` tiene el mismo defecto: "Saldo de préstamos en mora" (y=258,
  height=14, termina en y=272) se superpone con el título siguiente "Composición de lo cobrado
  en el período" (y=262) — mismo arreglo que PA-25, pendiente también acá. **Hallazgo nuevo,
  distinto**: en ese mismo archivo, el pie de página "Emitido por: ..." (`pageFooter`, banda
  propia de altura fija) puede superponerse con la última línea del cuerpo, "Demanda por tramo
  de plazo" (`$P{DEMANDA_POR_PLAZO_TEXTO}`, con texto tipo "0-6 meses... · 7-12 meses...") — esa
  línea es `isStretchWithOverflow="true"` dentro de la banda `<title>` (sin `positionType="Float"`
  a nivel de banda que reserve más alto cuando el texto crece), así que si el texto de los tramos
  es largo, su contenido real queda más abajo de lo que la banda declaró y se mezcla con el pie.
  Mismo lote que PA-10 (espacio de más en "Página X de Y") y PA-25 — Lote 2, estilo PDF.

## 11. Guion para la defensa

"RF31 exige 4 informes financieros para el dueño: resumen financiero (rango arbitrario),
resumen mensual, cartera y proyección a 30/60/90 días. Los 4 existen con ese contenido exacto, y
el acceso está restringido de verdad: una lista blanca en el servidor excluye al oficial de
estos 4 informes aunque tenga permiso de consulta general sobre reportes — el cajero ni llega a
`/reportes`. Las cifras de cobrado nunca incluyen un pago revertido ni una factura anulada,
mismo filtro que confirmamos hoy en RF25/RF30."
