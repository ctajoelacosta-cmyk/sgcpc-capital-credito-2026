# RF34 — Estado de cuenta del préstamo (historial y reimpresión)

**Estado: probado en pantalla el 13/10/2026 (tag `rf34-probado`).** Historial completo,
reimpresión de documentos, e Informe PDF/Excel-CSV nuevos (FALLA-1 de la auditoría de impresión,
ver §7), verificados como oficial, cajera y gerente (implementado en etapas E7/E9, con el
agregado del comprobante de desembolso en Bloque 7/E10, D-130).

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF34 (módulo Informes): *"El sistema debe mostrar el estado de
cuenta de cada préstamo con su historial completo y permitir reimprimir sus documentos."*

## 2. Cómo funciona

`GET /reportes/estado-cuenta` (búsqueda) → `/reportes/estado-cuenta/{idPrestamo}` (ficha) —
**ruta propia bajo `/reportes`, deliberadamente separada de `/cobranza`** (comentario del
código, línea 287-289: *"corrección de Joel ('no le da al oficial acceso a /cobranza, que
sigue con 403'). Mismo PDF que ServicioCobranza ya genera; solo cambia quién puede pedirlo."*) —
así el oficial (que no tiene `Permiso.COBRAR`) puede ver el estado de cuenta y reimprimir
comprobantes sin que eso le abra la ficha de cobranza en sí.

**Historial**: `historialCobros` (lista de `FilaHistorialCobranza`) — todos los cobros del
préstamo, incluidos los revertidos (con su marca, mismo criterio que RF32 §6b), más los datos
del préstamo (estado, saldo, atrasos).

**Reimpresión**, por el mismo PDF que ya genera `ServicioCobranza` (nunca un PDF nuevo ni
recalculado — D-94, "generar al primer pedido y guardar"):
- `recibo`, `factura`, `constancia` (cancelación o reversión), `nota-anulacion` — 4 tipos, vía
  `GET /reportes/estado-cuenta/{id}/{tipo}/{idDocumento}`.
- **Comprobante de desembolso** (D-130, agregado después): enlace directo a
  `/desembolso/{id}/comprobante` desde esta misma ficha.
- **Informe PDF / Excel-CSV de la ficha completa** (13/10/2026, auditoría de impresión, ver §7):
  `GET /reportes/estado-cuenta/{id}/informe` y `GET /reportes/estado-cuenta/{id}/csv` — el
  préstamo, el plan de pagos, el historial de cobros y las gestiones de cobranza, todo en un solo
  documento con el encabezado común (logo/empresa). Reemplaza al botón "Imprimir" que antes usaba
  `window.print()`.
- Contrato/pagaré/plan de pagos **no** tienen un enlace propio desde esta pantalla — no hace
  falta: viven bajo `/formalizacion/{id}`, al que el oficial ya tiene acceso **directo**
  (`GENERAR_DOCUMENTOS_FORMALIZACION = TOTAL`). Esta pantalla cubre específicamente los
  documentos que de otro modo exigirían pasar por `/cobranza` o `/desembolso` (rutas del
  cajero), no los que el oficial ya puede reimprimir por su cuenta.

## 3. Auditoría

Ninguna propia por ver la ficha o reimprimir — cada PDF reimpreso usa el mismo mecanismo
cacheado de `ServicioCobranza` (D-94), sin volver a auditar la generación (el evento real ya se
auditó cuando se emitió la primera vez).

## 4. Dónde está

- **Controlador**: `ControladorReportesServlet`, caso `"estado-cuenta"`,
  `descargarComprobanteEstadoCuenta`, `mostrarEstadoCuentaFicha`, `mostrarEstadoCuentaBusqueda`,
  y (13/10/2026) `generarInformeEstadoCuentaPdf`/`exportarEstadoCuentaCsv`.
- **Vistas**: `reportes/estado_cuenta_busqueda.jsp`, `reportes/estado_cuenta_ficha.jsp`.
- **Servicio reutilizado**: `ServicioCobranza.obtenerPdfRecibo`/`.obtenerPdfFactura`/
  `.obtenerPdfConstancia`/`.obtenerPdfNotaAnulacion` — los mismos 4 métodos que usa `/cobranza`,
  sin duplicar lógica.
- **Informe PDF/CSV** (13/10/2026): `ServicioReportes.listarGestionesEstadoCuenta`/
  `.construirAvisoPlanEstadoCuenta` (nuevos) + `ServicioInformes.generarEstadoCuentaPdf`/
  `.auditarExportacionCsvEstadoCuenta` (nuevos) + `estado_cuenta.jrxml` con sus dos subreportes
  `estado_cuenta_historial.jrxml`/`estado_cuenta_gestiones.jrxml`.
- **Permisos**: `estado-cuenta` está en `SUBREPORTES_OFICIAL` (ver RF31/RF32 §2) — oficial y
  gerente/administrador la ven; cajero no (no tiene `VER_REPORTES`, pero tampoco la necesita:
  ya ve el historial completo directo en `/cobranza/{id}`).

## 5. Dependencias

- **RF23/RF25** (recibo/factura/constancia/nota de anulación): esta pantalla no genera ningún
  documento nuevo, solo reexpone los mismos 4 tipos que esos RF ya emiten.
- **RF17/RF18** (comprobante de desembolso): enlace directo, mismo documento.
- **RF20-RF27** (cobranza): el historial que se muestra acá es el mismo
  `listarTodosPorPrestamo`/`listarHistorialCobros` que usa `cobranza/ficha.jsp`.

## 6. Análisis

**a) ¿Muestra el historial completo?** **Sí** — incluye los pagos revertidos (marcados, no
ocultos, RN-13), igual que el historial de `/cobranza/{id}`; no es una versión recortada.

**b) ¿Permite reimprimir los documentos?** **Sí**, 5 tipos reales (recibo, factura, constancia,
nota de anulación, comprobante de desembolso) más el acceso directo a contrato/pagaré/plan por
otra vía (§2) — cubre, en los hechos, **todos** los documentos de un préstamo, aunque no todos
pasen literalmente por esta misma pantalla.

**c) ¿Resuelve un problema real de permisos, o es solo un atajo cosmético?** **Resuelve un
problema real**, documentado en el propio código: sin esta ruta separada, el oficial no podría
reimprimir un recibo o una factura (esos PDF viven detrás de `/cobranza`, que es del cajero) sin
que se le abriera además toda la ficha de cobro. La separación de rutas es la validación real
(permiso distinto, no una pantalla distinta sobre el mismo permiso).

**d) ¿Depende de fechas?** No — un préstamo con cualquier historial real (aunque sea un solo
pago) alcanza para probar la pantalla completa; no depende de que algo esté vencido.

**e) ¿Algo miente o confunde?** No se encontró ningún texto falso. No hay ningún hallazgo nuevo
más allá de lo ya señalado para los documentos que reexpone (RF23 §10, revisión del contador).

## 7. Decisiones

Ninguna nueva sobre la separación de rutas — ya fue una decisión explícita de Joel (línea
287-289 del código), documentada ahí mismo.

**(13/10/2026, auditoría de impresión, hallazgo FALLA-1 de Joel, corregido)**: el botón
"Imprimir" de esta ficha usaba `window.print()` sobre la propia pantalla, con una hoja de
estilos de impresión propia (`<style media="print">`, **Decisión 3 del Bloque 4/E8**: *"en
pantalla no cambia nada, al imprimir esconde el menú/cabecera/botones... para que salga
prolijo en papel sin tener que diseñar un PDF nuevo"*) — una decisión válida en su momento, pero
que quedó en contra de la regla actual del proyecto: *todo documento o informe que se imprime o
se entrega sale en PDF generado con JasperReports, con el estilo común (logo, empresa, RUC,
"Emitido por", "Página N de M")*, igual que los otros 21 informes de este bloque. Era, de hecho,
el único lugar de todo el sistema con este patrón (confirmado por un grep de `window.print`/
`@media print` en todo `src/main/webapp`).

**Corregido**: nuevo `estado_cuenta.jrxml` (con dos subreportes propios, `estado_cuenta_historial.
jrxml`/`estado_cuenta_gestiones.jrxml`, para las tablas de largo variable — ver nota de diseño en
el propio `.jrxml`: se probó primero con el componente `List` de JasperReports para las tres
tablas en un solo archivo, pero no empuja correctamente el contenido que viene después cuando se
estira; un subreporte sí lo hace, patrón estándar y ya probado con un harness de prueba sin base
de datos). Datos: los mismos que ya arma `mostrarEstadoCuentaFicha` (préstamo, cliente, totales,
plan de pagos completo, historial de cobros, gestiones de cobranza — reusando
`ServicioReportes.listarPlanDePagos`/`.obtenerHistorialCobros`, más un método nuevo
`.listarGestionesEstadoCuenta`, mismo molde de fila `FilaGestionReporte` que ya usa
`/reportes/gestiones`, sin inventar un DTO). Nuevas rutas `GET /reportes/estado-cuenta/{id}/
informe` (PDF) y `GET /reportes/estado-cuenta/{id}/csv` (CSV, mismas 3 secciones: datos del
préstamo, plan de pagos, historial, gestiones), ambas auditadas (`EMITIR_INFORME_PDF`/
`EXPORTAR_CSV`, entidad `ESTADO_CUENTA`, igual que los demás informes). El botón "Imprimir" pasa
a "Informe PDF" + "Excel/CSV" (mismo ícono/texto que el resto de la pantalla de Reportes); se
retira el `<style media="print">` y el `onclick="window.print()"`. Caso límite probado con el
mismo harness (`ANULADO_ANTES_DESEMBOLSO`, sin cuotas/historial/gestiones):
`whenNoDataType="AllSectionsNoDetail"` en `estado_cuenta.jrxml` (mismo patrón que `recibo.jrxml`/
`factura.jrxml`/`arqueo_caja.jrxml`) y `whenNoDataType="NoDataSection"` en los dos subreportes
(con su propio `<noData>`, "Sin cobros registrados."/"Sin gestiones registradas.") para que el
encabezado y el aviso sigan imprimiendo igual, en vez de una página en blanco.

## 8. Historial de cambios

| Fecha | Cambio | Commit | Tag |
|---|---|---|---|
| — | Implementado en etapas E7/E9. | — | — |
| — | D-130 (Bloque 7/E10): agregado el enlace de reimpresión del comprobante de desembolso. | — | — |
| 13/10/2026 | Primer análisis contra el anteproyecto (este documento), sin cambios de código. | — | — |
| 13/10/2026 | FALLA-1 de la auditoría de impresión: `window.print()` reemplazado por `estado_cuenta.jrxml` (Informe PDF + Excel/CSV), ver §7. `mvn test`: 724/724 en verde. | — | — |
| 13/10/2026 | Probado en pantalla (oficial/cajera/gerente), ver §9. | *(pendiente)* | `rf34-probado` |

| 30/10/2026 | **B93**: la búsqueda por número de operación pasa a ser **la misma que usa Cobranza** (`ServicioCobranza.buscarPrestamosPorNumeroOperacion`, ver [RF20_lista_trabajo_cobranza.md](RF20_lista_trabajo_cobranza.md) y **B82**). Corrige dos cosas que hacían que las dos pantallas contestaran distinto a lo mismo: acá el texto se comparaba **tal cual se escribía**, así que `cc-2026-00005` en minúsculas no encontraba nada (y en Cobranza sí, porque esa lo pasaba a mayúsculas), y ninguna de las dos aceptaba un **fragmento**. Con un fragmento ambiguo no se elige un préstamo al azar: se pide el número completo, con el mismo texto que Cobranza. La búsqueda por cédula no se tocó (sigue con `normalizarCedula`, D-117, e incluye los FINALIZADO). **Probado en pantalla el mismo día** (§9). `mvn test` 858/858 | `9b66988` | — |

## 9. Pruebas realizadas en pantalla

**Probado por Joel el 13/10/2026 (tag `rf34-probado`)**:

1. **Como oficial**, estado de cuenta de `CC-2026-00001`: "Informe PDF" con logo y encabezado
   común, saldo 4.037.365, pagado 965.150, exigible 0, 10 cuotas, REC-2026-000003 marcado
   "Revertido: Sí", sección de gestiones presente. "Excel/CSV": acentos correctos, montos como
   número (no texto), las 3 secciones (préstamo, plan de pagos, historial, gestiones).
   Reimpresión de recibo (con la franja de anulado) y de factura desde la ficha: OK.
2. **Como cajera**, "Informes" queda con candado (sin `VER_REPORTES`) y forzar la URL
   `/reportes/estado-cuenta/1/informe` da **403** real.
3. **Como gerente**, Cartera (pantalla y PDF) sin cambios — confirma que el cambio de esta
   corrección no afectó otros informes del mismo controlador.

Dos cosméticos anotados para Lote 2 (`AVANCE.md`): "Página 1 de    2" con el espacio de más
(extiende PA-10, mismo patrón que el resto de los informes); el CSV no dice "Sin gestiones
registradas" cuando no hay ninguna (PA-27, el PDF sí lo dice).

## 10. Pendientes y trabajo futuro

- PA-10 (Lote 2, cosmético): "Página 1 de    2" con el espacio de más en `estado_cuenta.jrxml`.
- PA-27 (Lote 2, cosmético): el CSV no dice "Sin gestiones registradas"/"Sin cobros registrados"
  cuando una sección queda vacía (el PDF sí lo dice, con la banda `noData` de cada subreporte).
- ~~Borrar, antes de la entrega, los archivos temporales de verificación usados para esta
  corrección: `VerificarEstadoCuentaJrxmlTemporal.java` y los 2 PDF de muestra en
  `herramientas/muestras_lote2/estado_cuenta_muestra*.pdf`.~~ — **resuelto (18/10/2026, ronda de
  código muerto)**: los 13 archivos de `herramientas/*Temporal`/`Verificar*.java` (incluido
  este) y las carpetas de muestras se borraron junto con el resto. Ver `AVANCE.md`.

## 11. Guion para la defensa

"RF34 exige el estado de cuenta con historial completo y reimpresión de documentos. La pantalla
vive en una ruta propia, separada de `/cobranza`, a propósito: así el oficial puede reimprimir
recibos y facturas sin que eso le abra la ficha de cobro completa, que sigue siendo exclusiva
del cajero. El historial nunca oculta un cobro revertido — mismo criterio de 'nada se borra' que
el resto del sistema."
