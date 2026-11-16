# RF28 — Constancia de cancelación y préstamo finalizado

**Estado: probado en pantalla el 12/10/2026 (tag `rf28-probado`).**

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF28 (módulo Cancelación): *"El sistema debe emitir la constancia de
cancelación y dejar el préstamo como finalizado, sin deuda pendiente."*

Regla relacionada: **RN-12** ("Saldo total en cero → préstamo FINALIZADO. La devolución del
pagaré se registra aparte, con constancia firmada." — la devolución del pagaré es RF29, un paso
distinto y posterior; ver `RF29_cierre_documental.md`).

## 2. Cómo funciona

Ambos efectos de este RF ocurren dentro de la **misma transacción** que RF26
(`ServicioCobranza.confirmarCancelacionTotal`, ver `RF26_cancelacion_anticipada.md` §2) — no hay
un paso separado "emitir constancia" que pueda quedar a mitad de camino:

1. Se asigna el número de constancia `CAN-aaaa-nnnnnn` (`NumeradorDAO`, secuencia `"CONSTANCIA"`)
   **antes** de grabar nada, para que la correlatividad siga el orden real de las operaciones
   (D-93/D-94, mismo criterio que `numero_recibo`).
2. Se graba en `liquidaciones_anticipadas.numero_constancia` (fila `tipo_liquidacion = 'TOTAL'`).
3. `PrestamoDAO.marcarFinalizado(idPrestamo)` → `prestamos.estado_prestamo = 'FINALIZADO'`.
4. El PDF de la constancia **no se genera en el momento de cancelar** — sigue el patrón D-94
   ("generar al primer pedido y guardar"): se genera recién cuando alguien la pide por primera
   vez (`ServicioCobranza.obtenerPdfConstancia`, cacheada en `archivos_binarios`), igual que
   recibo/factura. El enlace "Imprimir constancia de cancelación" (`/cobranza/{id}/constancia/
   {idLiquidacion}`) queda disponible de inmediato en el modal de éxito (D-131) y, después,
   también en el historial de la ficha de cobranza (D-139 — se puede reimprimir aunque el
   préstamo ya esté FINALIZADO).

## 3. Auditoría

Ninguna propia además de la de RF26 (`CANCELAR_TOTAL`) — el cambio de estado a FINALIZADO y la
constancia nacen en la misma operación auditada, no hay un evento separado.

## 4. Dónde está

- **Servicio**: `ServicioCobranza.confirmarCancelacionTotal` (asigna el número y marca
  FINALIZADO), `.obtenerPdfConstancia` (genera/cachea el PDF, `reportes/constancia_cancelacion.
  jasper`).
- **DAO**: `PrestamoDAO.marcarFinalizado`; `LiquidacionAnticipadaDAO` (número de constancia).
- **Controlador**: `ControladorCobranzaServlet` — descarga en `GET /cobranza/{id}/constancia/
  {idLiquidacion}` (línea ~132), enlace armado en `procesarCancelar` (línea ~336-349).
- **Vista**: modal de éxito (D-131) tras cancelar; historial de la ficha de cobranza
  (`cobranza/ficha.jsp`, reimpresión).
- **Reporte**: `reportes_jasper/constancia_cancelacion.jrxml`/`.jasper`.

## 5. Dependencias

- **RF26**: es quien dispara ambos efectos de RF28 — no se puede probar RF28 sin pasar antes por
  una cancelación total real.
- **RF29**: el siguiente paso, independiente — un préstamo FINALIZADO por RF28 queda con
  `estado_cierre_documental = 'PENDIENTE'` (ver `RF29_cierre_documental.md`), que es un proceso
  documental aparte, no financiero.
- **RF23** (recibo/factura): mismo mecanismo de generación/cacheo de PDF (D-94) que la
  constancia.

## 6. Análisis

**a) ¿Emite la constancia?** **Sí.** El número `CAN-aaaa-nnnnnn` se asigna en el momento exacto
de la cancelación (no al imprimir), así que la constancia existe desde ese instante aunque el
PDF en sí se genere más tarde, al pedirlo — esto es consistente con el resto del sistema
(recibo, factura) y no contradice "debe emitir la constancia": lo que el anteproyecto exige es
que la constancia **exista y sea obtenible**, no que el PDF se genere en ese preciso milisegundo.

**b) ¿Deja el préstamo como finalizado, sin deuda pendiente?** **Sí**, verificado contra el
código: `confirmarCancelacionTotal` marca **todas** las cuotas pendientes como PAGADAS
(`liquidacion.getCuotasResultantes()`, ver RF26 §2 punto 5) y recién después llama a
`marcarFinalizado` — no hay forma de que el préstamo quede FINALIZADO con una cuota todavía
pendiente, porque es la misma transacción (si algo falla a mitad de camino, el `rollback` revierte
todo, incluido el cambio de estado).

**c) ¿Depende de fechas?** Indirectamente, a través de RF26 (el monto de la cancelación depende
de la fecha) — RF28 en sí (asignar el número, marcar FINALIZADO) no agrega ninguna dependencia
de fecha propia.

**d) ¿Algo miente o confunde?** No se encontró ningún incumplimiento. Un punto a verificar en
pantalla (no es un error de código, es una verificación visual pendiente): que el PDF de la
constancia muestre correctamente "sin deuda pendiente" — el reporte usa
`FECHA_CANCELACION`/`NUMERO_CONSTANCIA_CANCELACION` armados en `ServicioFormalizacion` (compartido
con RF29, ver ese documento §2) y en `ServicioCobranza.obtenerPdfConstancia`; falta confirmar en
pantalla que el texto impreso sea claro para el cliente (no solo técnicamente correcto).

## 7. Decisiones

Ninguna nueva — RF28 es, en los hechos, parte de la misma operación que RF26 desde su diseño
original en E7; no hubo una decisión de diseño propia que documentar aparte.

## 8. Historial de cambios

| Fecha | Cambio | Commit | Tag |
|---|---|---|---|
| — | Implementado en la etapa E7 (dentro de `confirmarCancelacionTotal`), sin registro separado en `AVANCE.md` como una decisión propia (es parte del diseño original de la cancelación total). | — | `d145-caja-simple` |
| 12/10/2026 | Primer análisis contra el anteproyecto (este documento), sin cambios de código. | *(pendiente)* | — |

## 9. Pruebas realizadas en pantalla

**12/10/2026, prueba de Joel, CC-2026-00026**: tras "Cancelar todo" (RF26), el préstamo quedó
`FINALIZADO` sin ninguna cuota pendiente; constancia **CAN-2026-000004**; factura
**001-001-0000014** correcta — solo intereses/mora con su IVA, **sin capital** (RN-25, "el
capital se documenta en el recibo, no en la factura"). Todo OK.

## 10. Pendientes y trabajo futuro

- **(12/10/2026, pedido de Joel)** Ajustes de texto en el PDF de la constancia (logo, sin "Sr./
  Sra.", nombre sin mayúsculas forzadas, "N°"→"N.º", total en letras) — mismo criterio ya
  aprobado en la constancia de reversión (RF25); ver `AVANCE.md`/commit de ajustes chicos del
  Bloque Cancelación.

## 11. Guion para la defensa

"RF28 exige emitir la constancia de cancelación y dejar el préstamo finalizado sin deuda
pendiente. Ambos efectos ocurren en la misma transacción que la cancelación total (RF26): el
número de constancia se asigna en el momento, todas las cuotas pendientes quedan pagadas y el
préstamo pasa a FINALIZADO antes de confirmar — si algo fallara a mitad de camino, la transacción
completa se revierte, así que nunca queda un estado a medias."
