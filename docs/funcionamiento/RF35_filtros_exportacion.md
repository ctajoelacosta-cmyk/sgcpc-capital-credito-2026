# RF35 — Filtro por fechas y exportación a PDF y Excel (CSV)

**Estado: probado en pantalla el 13/10/2026 (tag `rf35-probado`).** Filtro por fecha/mes,
exportación a PDF y a Excel/CSV, auditoría de ambas — verificado con el resumen mensual de
octubre 2026 (mecanismo transversal, construido incrementalmente desde Bloque 4/E8 hasta Bloque
7/E10).

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF35 (módulo Informes): *"El sistema debe permitir filtrar los
informes por rango de fechas y exportarlos en PDF y en Excel (CSV)."*

Es un requisito **transversal** (aplica a todos los informes de RF31/RF32/RF33), no un informe
propio — por eso este documento analiza el **mecanismo común**, no un contenido particular.

## 2. Cómo funciona

- **Filtro por fecha**: cada informe recibe `desde`/`hasta` (o `mes`, para los mensuales) como
  parámetros de query string, parseados con `parsearFecha`/`parsearMes` (con un valor por
  defecto razonable si no se pasa nada — ej. la semana/mes actual) — nunca un filtro fijo sin
  poder cambiarlo.
- **Exportación PDF**: `GeneradorInformesPdf` + JasperReports (mismo motor que facturas/recibos/
  contratos), un método `generarInforme*Pdf` por informe.
- **Exportación CSV ("Excel")**: `ExportadorCsv` (clase común desde Bloque 7/E10, B1 — antes
  cada controlador tenía su propia copia de `abrirCsv()`/`comilla()`/`limpiar()`), con 3
  decisiones de robustez reales, no cosméticas:
  1. **BOM UTF-8 + separador `;`** (en vez de `,`) — para que Excel en español abra el archivo
     con los acentos correctos y sin confundir la coma decimal paraguaya con el separador de
     columnas.
  2. **`comoTexto()`** — fuerza como texto literal (fórmula `="..."` + comillas RFC 4180) a
     cualquier columna que Excel podría "ayudar" convirtiendo en número y perdiendo un cero
     inicial (teléfono, C.I., número de operación/recibo/factura/comprobante) — bug real
     encontrado por Joel al probar el CSV de Clientes (comentario "E-7" en el código).
  3. **`limpiar()`** — vuelve numérico (sumable en la planilla) un monto que ya viene formateado
     como texto ("1.234.567 Gs."), quitando el sufijo y los puntos de miles.

## 3. Auditoría

`ServicioAuditoria`, un evento `EXPORTAR_CSV_<INFORME>` por cada descarga CSV (`auditarExportacionCsv*`,
un método por informe en `ServicioInformes`/`ServicioReportes`) — **con una excepción real, ver
§6c**. La exportación a PDF no se audita (ver nota).

## 4. Dónde está

- **Infraestructura común**: `infraestructura/ExportadorCsv` (CSV), `GeneradorInformesPdf`
  (PDF) — ambas reutilizadas por **todos** los informes de RF31/RF32/RF33, nunca una copia por
  informe.
- **Controlador**: `ControladorReportesServlet`, un `case "<informe>/csv"` (o `"/exportar"`,
  nombre inconsistente entre informes, ver §6d) por cada uno, junto a su `case "<informe>/informe"`
  (PDF) y el `case "<informe>"` simple (pantalla con el filtro).

## 5. Dependencias

- **RF30-RF34**: este mecanismo es el que usan todos ellos para exportar — no se repite en
  cada documento de RF, se analiza acá una sola vez.

## 6. Análisis

**a-nota**: existe además un pedido de Joel (07/10/2026, "OBLIGATORIA"), **fuera del texto
firmado de este RF** (que solo exige rango de fechas), de un filtro adicional por "Cliente u
operación"/"C.I." en 7 informes de RF32 — confirmado en este análisis que todavía no está
implementado. Se documenta en `RF32_informes_operacion.md` §6g/§10 (es un pendiente de RF32, no
un incumplimiento de RF35 en sí, ya que el texto firmado de RF35 no lo exige).

**a) ¿Permite filtrar por rango de fechas?** **Sí**, en los informes que tienen sentido con un
rango (resumen financiero, cobros, desembolsos, solicitudes, aprobaciones, vencimientos,
gestiones, cierres documentales, reversiones, auditoría) — `desde`/`hasta` reales, no fijos. Los
que son inherentemente "a una fecha" (cartera, morosidad, incobrables) o "a un mes" (resumen
mensual, IVA, facturas anuladas) usan ese criterio en vez de un rango, consistente con su propio
contenido (no tendría sentido un "resumen mensual" con rango arbitrario — para eso existe el
resumen financiero, que sí lo tiene, D-128).

**b) ¿Exporta a PDF y a CSV?** **Sí, verificado para los 2 informes "para el dueño" (RF31) y
los 9 "de la operación" (RF32) y los 2 fiscales (RF33)** — cada uno tiene su `generarInforme*Pdf`
y su exportación CSV correspondiente.

**c) (encontrado el 13/10/2026, corregido el mismo día, pendiente de prueba en pantalla) Faltaban
2 exportaciones CSV sin auditar.** `descargarResumenMensualCsv`/`descargarResumenFinancieroCsv`
no llamaban a ningún método de auditoría — a diferencia de **todos** los demás informes (iva,
incobrables, facturas-anuladas, caja, auditoría, vencimientos, morosidad, gestiones, cartera,
solicitudes, aprobaciones, desembolsos, cobros, clientes, cierres documentales: los 15 sí
llamaban a su `auditarExportacionCsv*` correspondiente). **Corregido**: `ServicioInformes.
auditarExportacionCsvResumenMensual`/`.auditarExportacionCsvResumenFinanciero` (mismo patrón que
los demás), llamados desde el controlador antes de escribir el CSV.

**d) Nombres de ruta inconsistentes entre informes** (hallazgo menor, cosmético): algunos usan
`/csv` (`iva/csv`, `resumen-mensual/csv`, `resumen-financiero/csv`, `facturas-anuladas/csv`) y
otros `/exportar` (`caja/exportar`, `incobrables/exportar`, `auditoria/exportar`) para la misma
acción (descargar CSV). No afecta al usuario (son enlaces, nunca una URL que tipee a mano), pero
es una inconsistencia de nombres que vale unificar algún día.

**e) (corrección propia, 13/10/2026) ¿El PDF se audita?** **Sí, en los 17 informes** — cada
`generarInforme*Pdf` llama a `servicioAuditoria.registrar(..., "EMITIR_INFORME_PDF", ...)`,
incluidos resumen mensual/financiero (que sí lo tenían — solo el CSV les faltaba, ver §6c).
**Esta sección decía antes "el PDF nunca se audita en ningún informe" — era incorrecto,
verificado recién con una lectura más completa del código; se corrige acá.**

**f) ¿Algo miente o confunde?** No se encontró ningún texto falso. Los hallazgos de §6c/§6d son
de completitud/consistencia, no de contenido incorrecto.

**g) (13/10/2026, hallazgo de Joel probando en pantalla, PA-28) Las entradas `EXPORTAR_CSV`/
`EMITIR_INFORME_PDF` no guardan IP ni navegador.** Confirmado por lectura de código:
`ServicioAuditoria.registrar` tiene dos variantes — una de 5 parámetros (sin IP/navegador) y
otra de 7 (con `direccionIp`/`navegador`, comentario "Bloque de seguridad, hallazgo de Joel").
Las 50 llamadas de `ServicioInformes` a `EXPORTAR_CSV`/`EMITIR_INFORME_PDF` usan la variante de 5
parámetros; en cambio `ServicioAutenticacion` (`ACCESO_CONCEDIDO`, `ACCESO_FALLIDO`,
`CERRAR_SESION`, etc.) y `FiltroAutorizacion`/`ListenerSesion` (`ACCESO_DENEGADO`,
`SESION_EXPIRADA`) sí usan la variante con IP/navegador. No es un error de un informe puntual:
es que la exportación de informes, como clase, nunca pasó por la variante de seguridad.

**h) (13/10/2026, hallazgo de Joel probando en pantalla, PA-29) El CSV del resumen mensual no
dice el período/fecha de emisión/usuario en su contenido.** Confirmado:
`ControladorReportesServlet.descargarResumenMensualCsv` (línea ~692-707) escribe 12 filas
"Concepto;Valor" (préstamos otorgados, cobrado por concepto, ganancia, morosidad ×3) — ninguna
dice el mes, la fecha de emisión ni el usuario que lo generó; solo el **nombre del archivo**
(`resumen_mensual_2026-10.csv`) indica el período. El PDF del mismo informe sí lleva esos 3 datos
(parámetros `FECHA_EMISION`/`USUARIO_EMISOR` del encabezado común), así que es una asimetría
entre el PDF y el CSV del mismo informe, no una falla aislada de uno solo.

## 7. Decisiones

Ninguna nueva.

## 8. Historial de cambios

| Fecha | Cambio | Commit | Tag |
|---|---|---|---|
| — | Construido incrementalmente desde Bloque 4/E8 (primeros CSV) hasta Bloque 7/E10, B1 (unificación en `ExportadorCsv`) y Tanda D, D-128 (resumen financiero/proyección). | — | — |
| 13/10/2026 | Primer análisis contra el anteproyecto (este documento), sin cambios de código. Encontradas 2 exportaciones CSV sin auditar (§6c) y nombres de ruta inconsistentes (§6d). | — | — |
| 13/10/2026 | Corregido §6c: `auditarExportacionCsvResumenMensual`/`.auditarExportacionCsvResumenFinanciero` + 2 pruebas unitarias nuevas. `mvn test` 721/0. | — | — |
| 13/10/2026 | Probado en pantalla (resumen mensual, gerente), ver §9. Encontrados PA-28/PA-29 (§6g/§6h), anotados en `AVANCE.md` como mejoras de bajo riesgo, sin implementar. Sin cambios de código. | *(pendiente)* | `rf35-probado` |

## 9. Pruebas realizadas en pantalla

**13/10/2026, Cobros (gerente)**: filtro "Cliente u operación" probado (ver RF32 §9) — mismo
mecanismo transversal que usan los 6 informes de este RF.

**Probado por Joel el 13/10/2026 (gerente, base `sgcpc_db_pruebas`, tag `rf35-probado`)**:

1. Resumen mensual de octubre 2026: composición correcta (total cobrado 5.898.961, ganancia
   219.345) y morosidad en sus 3 líneas (28,57 %, 9,32 %, 59,52 %) — coincide con RF31/RF33.
2. "Descargar CSV": los 12 valores coinciden con la pantalla, acentos correctos, números sin
   separadores (sumables).
3. Auditoría: queda registrado `EXPORTAR_CSV` / `INFORME_RESUMEN_MENSUAL` con usuario y hora
   (15:16) — confirma el fix de §6c.

Dos mejoras de bajo riesgo encontradas en esta prueba, anotadas en `AVANCE.md` (PA-28/PA-29,
ver §6g/§6h) — no implementadas, a la espera de que Joel decida cuándo.

## 10. Pendientes y trabajo futuro

- PA-28 (`AVANCE.md`, bajo riesgo): `EXPORTAR_CSV`/`EMITIR_INFORME_PDF` no guardan IP ni
  navegador, a diferencia de las entradas de sesión (§6g).
- PA-29 (`AVANCE.md`, bajo riesgo): el CSV del resumen mensual no indica período/fecha de
  emisión/usuario en su contenido, solo en el nombre del archivo (§6h).
- **(13/10/2026, cosmético, prioridad baja)** Unificar el nombre de ruta de descarga CSV
  (`/csv` vs. `/exportar`) entre todos los informes (§6d).
- **Prueba en pantalla**: confirmar que los demás informes (más allá del resumen mensual, ya
  probado) exportan un CSV que abre bien en Excel en español — el código ya lo garantiza (§2),
  falta la verificación visual de los restantes.

## 11. Guion para la defensa

"RF35 exige filtrar por fecha y exportar a PDF y Excel, transversal a todos los informes. El
sistema tiene un mecanismo común (`ExportadorCsv`/`GeneradorInformesPdf`) reutilizado por los 13
informes de RF31-RF33, con 3 correcciones reales de compatibilidad con Excel en español (BOM,
separador `;`, cédulas/teléfonos que no pierden el cero inicial). En este análisis encontramos
una inconsistencia menor: 2 de los 17 informes no auditan su exportación CSV — queda propuesta
la corrección, pendiente de tu aprobación."
