# RF33 — Facturas del mes con IVA y facturas anuladas

**Estado: probado en pantalla el 13/10/2026 (tag `rf33-probado`).** IVA del mes y facturas
anuladas, verificados contra los datos reales de `sgcpc_db_pruebas` (implementado en Bloque
7/E10, con la lista de anuladas agregada después, Bloque C tanda 2).

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF33 (módulo Informes): *"El sistema debe generar el informe de las
facturas emitidas en el mes con su IVA y el de las facturas anuladas, para la liquidación de
impuestos."*

Regla relacionada: **RN-25** ("Toda factura muestra timbrado, vigencia, RUC y numeración
001-001-NNNNNNN correlativa sin saltos... el capital se documenta en el recibo, no en la
factura."). La correlatividad "sin saltos" es la razón de ser del diseño de §2 — ver más abajo.

## 2. Cómo funciona

**Dos informes, uno por el texto (IVA del mes) y uno aparte (anuladas), pero relacionados a
propósito**:

- **IVA del mes** (`/reportes/iva`, `ServicioReportes.listarIva`): trae las facturas `EMITIDA`
  **y** las `ANULADA` del mes, **mezcladas y ordenadas por número de factura** — a propósito,
  para que la numeración correlativa (RN-25, "sin saltos") se vea completa en el PDF: si solo se
  listaran las `EMITIDA`, un número anulado en el medio de la secuencia parecería un salto sin
  explicación. Cada fila anulada queda marcada (`FilaIva.isAnulada()`); el pie de página
  (`calcularTotalesIva`) **salta las anuladas de la suma** (`if (fila.isAnulada()) continue;`) —
  el total de IVA a pagar nunca incluye una factura que ya no es válida.
- **Facturas anuladas** (`/reportes/facturas-anuladas`, `ServicioReportes.listarFacturasAnuladas`):
  el mismo conjunto de anuladas del mes, pero como informe propio (comentario del código: "para
  la baja en Marangatu" — el sistema de la DNIT), con su propio detalle (motivo, quién autorizó
  la reversión que la anuló, nota de anulación — ver RF25).

Ambos, PDF + CSV (RF35), bajo `Permiso.VER_REPORTES` — **ninguno está en `SUBREPORTES_OFICIAL`**
(ver RF31/RF32 §2), así que solo gerente/administrador los ven; el oficial recibe 403 si fuerza
la URL.

## 3. Auditoría

Ninguna por generar/ver el PDF; la exportación CSV se audita (`auditarExportacionCsvIva`/
`auditarExportacionCsvFacturasAnuladas`), mismo patrón que el resto.

## 4. Dónde está

- **Servicio**: `ServicioReportes.listarIva`/`.calcularTotalesIva`/`.listarFacturasAnuladas`
  (línea 346-402) + `ServicioInformes.generarInformeIvaPdf`/`.generarInformeFacturasAnuladasPdf`.
- **DAO**: `FacturaDAO.listarPorRango` (línea 90, `WHERE estado = 'EMITIDA'`),
  `.listarAnuladasPorRango` (línea 127, `WHERE estado = 'ANULADA'`) — dos consultas separadas,
  nunca una sola sin filtro de estado.
- **DTO**: `FilaIva` (con `isAnulada()`), `TotalesIva`, `FilaFacturaAnulada`.
- **Controlador**: `ControladorReportesServlet`, casos `"iva"` y `"facturas-anuladas"`.
- **Vistas**: `reportes/iva.jsp`, `reportes/facturas-anuladas.jsp` (tarjetas ámbar en
  `reportes/indice.jsp`).

## 5. Dependencias

- **RF23** (factura): fuente única de los datos de cada factura (timbrado, IVA, totales) — este
  RF nunca recalcula nada, solo lista lo que RF23 ya emitió.
- **RF25** (reversión): una factura solo llega a `ANULADA` a través de una reversión autorizada
  (`ServicioReversiones.autorizarReversion` → `FacturaDAO.marcarAnulada`, con
  `numero_nota_anulacion` reservado en la misma transacción) — nunca por otro camino.
- **RF31/RF32**: comparten la whitelist de permisos (`SUBREPORTES_OFICIAL`), este RF queda del
  lado "para el dueño", igual que RF31.

## 6. Análisis

**a) ¿Cumple los 2 informes del texto firmado?** **Sí**, ambos existen con los nombres y el
propósito exacto ("para la liquidación de impuestos").

**b) (Atención especial pedida por Joel) ¿Una factura anulada se cuenta como IVA válido?**
**No, verificado con el mayor detalle posible**: `calcularTotalesIva` (línea 378-397) tiene un
`continue` explícito para cada fila `isAnulada()` — **ni siquiera se suma a `cantidad`** (el
conteo de facturas válidas), mucho menos a los montos de exenta/gravada/IVA/total. El propio
comentario del código (línea 399-401) lo deja escrito: "excluidas de IVA del mes... y de los
totales del resumen mensual/arqueo." Este es, con diferencia, el control más explícito y mejor
documentado de todo el bloque de informes para esta regla — no es una inferencia, es un
`continue` literal con un comentario que lo explica.

**c) ¿Por qué el informe de IVA sigue MOSTRANDO las anuladas si no las suma?** Decisión de
diseño correcta, no un descuido: RN-25 exige numeración "sin saltos" — si el PDF de IVA solo
mostrara las `EMITIDA`, un número faltante en la secuencia (ej. 001-001-0000005 anulada, no
aparece) se vería como un hueco sin explicar frente al contador/DNIT. Mostrarla igual, marcada
como anulada y en cero en el total, es la forma correcta de justificar el salto en la
numeración sin inventar ningún monto.

**d) ¿Depende de fechas?** Depende del mes elegido (`YearMonth`), no de que algo esté
"realmente vencido" — se puede probar con el mes actual y cualquier factura real ya emitida,
sin preparar datos especiales.

**e) ¿Algo miente o confunde?** No se encontró ningún texto falso ni ningún cálculo incorrecto.
Recordatorio ya documentado en `RF23_recibo_factura.md` §10 (no es un hallazgo nuevo de este
análisis): la factura en sí sigue **pendiente de revisión del contador** (DNIT, timbrado
autoimpresor vs. electrónica SIFEN) — este RF hereda esa misma reserva, no agrega una nueva.

## 7. Decisiones

Ninguna nueva — el diseño (mostrar anuladas marcadas, excluirlas de la suma) ya estaba decidido
y documentado en el código desde Bloque C tanda 2.

## 8. Historial de cambios

| Fecha | Cambio | Commit | Tag |
|---|---|---|---|
| — | IVA del mes implementado en Bloque 7/E10 (solo EMITIDA). | — | — |
| — | Bloque C, tanda 2 (pedido de Joel): IVA del mes agrega las ANULADA marcadas, sin sumarlas; informe "Facturas anuladas" agregado aparte. | — | — |
| 13/10/2026 | Primer análisis contra el anteproyecto (este documento), sin cambios de código. Confirmado el `continue` explícito que excluye las anuladas de la suma. | — | — |
| 13/10/2026 | Probado en pantalla, ver §9. Sin cambios de código. | *(pendiente)* | `rf33-probado` |

## 9. Pruebas realizadas en pantalla

**Probado por Joel el 13/10/2026 (gerente, tag `rf33-probado`)**:

1. `/reportes/iva` de octubre 2026: numeración 0000001-0000014 sin saltos; las 3 facturas
   0000011-13 marcadas **ANULADA** y fuera de los totales; total 11 facturas, gravada 241.282,
   IVA 21.937 (coincide con el IVA del resumen financiero, RF31) — confirma RN-25 (§6b/§6c). PDF
   igual que la pantalla.
2. `/reportes/facturas-anuladas` del mismo mes: las mismas 3 (con timbrado, nota NAN-2026-000001
   a 000003, fecha, motivo y quién anuló), total 663.888. PDF igual que la pantalla.

**27/10/2026 (Joel) — "IVA del mes" de octubre 2026 sobre la base actual: numeración sin saltos
pese a una prueba automática que tomó números y los revirtió.**

La prueba del 13/10/2026 fue sobre los datos de entonces. Esta pasada vale por otra cosa: entre
medio corrió `PruebaEmisionFacturaPdf` (RF23 §9), que recorre el circuito real de emisión
**dentro de una transacción que revierte**. El informe es la verificación independiente de que
ese rollback no dejó un agujero fiscal.

| | |
|---|---|
| Facturas de octubre 2026 (EMITIDA) | **5** — 001-001-**0000001** a **0000005** |
| Numeración | **correlativa, sin saltos** |
| Total gravada 10 % | **31.463** |
| IVA 10 % | **2.861** |

**Cuadra contra el arqueo del mismo día** (RF19 §9), que es lo que hace útil esta comprobación:

| | Gravada | IVA |
|---|---|---|
| Facturas de hoy (0000002 a 0000005) | 28.883 | **2.626** |
| Factura 0000001 (del 25/10) | 2.580 | 235 |
| **Total del mes** | **31.463** | **2.861** |

Los **2.626** de IVA de las facturas de hoy son exactamente el IVA que el arqueo de la sesión 7
sumó desde `aplicaciones_pago` — dos caminos independientes (el informe lee `facturas`; el arqueo
lee `aplicaciones_pago`) que dan el mismo número.

**Por qué la numeración no tiene saltos, aunque las secuencias internas sí**: `facturas.id_factura`
se saltea los ids 3, 4 y 5 (los consumió la transacción revertida de `PruebaEmisionFacturaPdf`),
pero el **número de factura** no, porque sale de `numeradores` y el `rollback` devolvió esa fila.
Es la diferencia entre una secuencia de PostgreSQL (no transaccional, se saltea) y el numerador
con `SELECT … FOR UPDATE` de `NumeradorDAO` (transaccional, no se saltea) — justamente el diseño
que RN-25 y D-89 exigen para un timbrado. Verificado: `numeradores` en `FACTURA/2026 = 5`.

**Evidencia — las cinco facturas del mes, completas**, en `evidencias\2026-10-27\facturas\`:
`001-001-0000001.pdf` (del 25/10) a `001-001-0000005.pdf`. Juntas suman los **31.463 / 2.861** de
este informe, así que la numeración sin saltos y el total del mes se pueden comprobar sobre los
PDF mismos, aun después de limpiar la base. Está también
`prueba_automatica_emision.pdf` — el PDF de la prueba cuyo número se devolvió con el `rollback`, y
que por eso **no** aparece en el informe. Ver `evidencias\2026-10-27\LEEME.md`.

## 10. Pendientes y trabajo futuro

- Pendiente heredado de RF23 (no nuevo acá): revisión del contador sobre el diseño legal de la
  factura.
- PA-25 (Lote 2, cosmético): "IVA de 2026-10" / "Facturas anuladas de 2026-10" → "octubre de
  2026" en `FILTROS_APLICADOS` (`ServicioInformes.construirTextoFiltrosIva`/
  `construirTextoFiltrosFacturasAnuladas`), mismo patrón que RF31.

## 11. Guion para la defensa

"RF33 exige el informe de IVA del mes y el de facturas anuladas, para la liquidación de
impuestos. El punto que pediste verificar: una factura anulada aparece en el informe de IVA
—marcada, para no dejar un hueco sin explicar en la numeración correlativa que exige RN-25— pero
un `continue` explícito en el cálculo del total la excluye de la suma; el código lo documenta
con un comentario propio. El informe de facturas anuladas es, además, el insumo para la baja en
el sistema de la DNIT (Marangatu)."
