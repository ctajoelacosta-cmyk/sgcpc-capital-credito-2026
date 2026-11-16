# RF18 — Movimientos de caja, saldo teórico y reimpresión de comprobantes

**Estado: cerrado y probado en pantalla completo el 11/10/2026 — tag `rf18-probado`.** Los
movimientos de la sesión (entrega del gerente, desembolso) y el saldo teórico quedaron
confirmados en pantalla con CC-2026-00001, cuadrando exacto (1.000.000 + 4.000.000 − 5.000.000 =
0). La reimpresión del comprobante de desembolso ya se confirmó en RF17. Queda 1 mejora menor
propia de RF18 (sección 10, b), que NO se hace en esta vuelta.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF18 (módulo Desembolso y caja):

> El sistema debe registrar los movimientos de la caja (entregas de efectivo del gerente,
> desembolsos y cobros), mostrar el saldo teórico y permitir reimprimir los comprobantes.

## 2. Cómo funciona

Cada movimiento de caja es una fila de `movimientos_caja` (`tipo`, `monto`, `signo`,
`referencia_tipo`/`id_referencia` apuntando al pago/desembolso real si corresponde,
`registrado_por`, `observación`) — nunca se edita ni se borra (`REGLAS_DESARROLLO.md` §5/RN-14).
Se insertan desde 3 lugares distintos, cada uno dueño de su propio tipo:

| Quién lo genera | Tipo | Signo | Referencia |
|---|---|---|---|
| `ServicioCaja.registrarEntregaGerente` | `ENTREGA_GERENTE` | +1 | `SESION_CAJA` |
| `ServicioDesembolso.confirmarDesembolso` | `DESEMBOLSO` | −1 | `DESEMBOLSO` |
| Cobranza (E7, otro RF) | `COBRO`/anticipo/cancelación/`VUELTO` | +1/−1 | `PAGO` |
| `ServicioCaja.cerrarCaja` | `RENDICION_GERENTE` | −1 | `SESION_CAJA` (no se cuenta en el saldo del día, es la salida del cierre) |

`MovimientoCajaDAO.sumarMovimientos` calcula `SUM(monto * signo)` sobre TODOS los movimientos de
la sesión — el saldo teórico es `saldo_inicial + esa suma` (`ServicioCaja.calcularSaldoTeorico`),
la misma fórmula que ya documentó `RF16_condiciones_desembolso.md` §6f.

## 3. Auditoría

Cada tipo de movimiento ya tiene su propio evento, documentado en su RF correspondiente:
`ENTREGAR_EFECTIVO_CAJA` (RF15), `DESEMBOLSAR` (RF17), `COBRAR`/similares (E7, otro RF),
`CERRAR_CAJA` (RF15, incluye la rendición). RF18 no agrega ningún evento propio — solo expone
lo que ya quedó registrado.

## 4. Dónde está

- `servicios/ServicioCaja.java` — `listarMovimientos`, `listarMovimientosConComprobante`,
  `calcularSaldoTeorico`.
- `dao/MovimientoCajaDAO.java` (`listarPorSesion`, `sumarMovimientos`).
- `webapp/WEB-INF/vistas/caja/ficha.jsp` — card "Sesión abierta" (saldo teórico actual) y card
  "Movimientos" (tabla completa de la sesión, con la columna "Comprobante" y el botón
  "Reimprimir" cuando corresponde).
- `controladores/ControladorCobranzaServlet.java` (reimpresión de recibo/factura — fuera del
  alcance de este documento, pertenece a E7), `controladores/ControladorDesembolsoServlet.java`
  (reimpresión de comprobante de desembolso, RF17).

## 5. Dependencias

- **Depende de**: RF15 (la sesión de caja existe), RF17 (desembolsos), E7 (cobros — fuera del
  alcance de este análisis, solo se documenta cómo SE VE en la tabla de movimientos).
- **Lo afecta un cambio en**: `MovimientoCajaDAO.sumarMovimientos` (fórmula única del saldo
  teórico, compartida con RF16 y con el arqueo de cierre).
- **A quién afecta un cambio acá**: el arqueo de cierre (`generarPdfArqueo`, RF15) usa
  exactamente los mismos movimientos, agrupados por tipo, para el resumen del PDF.

## 6. Análisis

### 11/10/2026 — revisión completa de RF18 contra el texto firmado (pedido de Joel, solo lectura)

**Método**: lectura completa de `ServicioCaja.listarMovimientosConComprobante`,
`caja/ficha.jsp` (cards "Sesión abierta" y "Movimientos"), `MovimientoCajaDAO.java`, y la matriz
de permisos para `VER_COMPROBANTES_COBRANZA`.

### e) Dónde se ven los movimientos, el saldo teórico, y desde dónde se reimprime cada uno

**Dónde se ven**: en `/caja`, mientras hay una sesión abierta — card "Sesión abierta" muestra
"Saldo teórico actual" (en negrita, siempre actualizado); card "Movimientos" lista TODOS los
movimientos de la sesión en curso, en una tabla (fecha/hora, tipo, monto con signo,
observación, comprobante, botón "Reimprimir" si corresponde). Una vez cerrada la sesión, el
historial sigue disponible desde "Sesiones cerradas" (últimas 10, o filtrando por fecha) y su
PDF de arqueo (RF15) — que repite el mismo resumen con los mismos movimientos, agrupados.

**Desde dónde se reimprime cada comprobante** (`listarMovimientosConComprobante`,
`ServicioCaja.java:324-350`):

| Tipo de movimiento | Tiene comprobante propio | Ruta de reimpresión |
|---|---|---|
| `COBRO`/anticipo/cancelación/`VUELTO` (`referencia_tipo = "PAGO"`) | Sí — el recibo del cobro | `/cobranza/{idPrestamo}/recibo/{idPago}` |
| `DESEMBOLSO` | Sí — el comprobante de desembolso (RF17) | `/desembolso/{idPrestamo}/comprobante` |
| `ENTREGA_GERENTE` | **No** | — (columna "Comprobante" queda "-", sin botón) |
| `RENDICION_GERENTE` | No (es la salida del cierre, no una operación con comprobante propio) | — |
| Compensación (reversiones, E9) | Depende del caso, fuera del alcance de este análisis | — |

**Quién puede reimprimir**: la columna "Reimprimir" solo se muestra a quien tiene
`Permiso.VER_COMPROBANTES_COBRANZA` en al menos `CONSULTA` — **gerente y cajera sí, oficial
no** (`NINGUNO`, `ServicioAutorizacion.java:52,89,118`). Doble capa, como ya es el patrón del
proyecto: la pantalla oculta el botón (`puedeVerComprobantesFicha`) Y la ruta de destino
(`/cobranza/.../recibo/...`, `/desembolso/.../comprobante`) está protegida con el mismo permiso
en el servidor — aunque alguien armara el enlace a mano, el 403 lo frena igual.

### Hallazgo (compartido con RF15/RF16, ya anotado en la prueba de pantalla)

La entrega de efectivo del gerente queda SIN comprobante propio (columna "-"). No es un
incumplimiento de RF18 (el texto solo pide "permitir reimprimir los comprobantes" — de las
entregas, cobros y desembolsos que SÍ tienen uno; la entrega del gerente nunca tuvo un
comprobante que generar, es un movimiento interno entre gerente y caja, no con el cliente) —
pero Joel ya señaló que valdría la pena un comprobante de entrega con firma del gerente y la
cajera, anotado en `RF15_apertura_caja.md`/`RF16_condiciones_desembolso.md` §10, no se repite
la propuesta acá.

### f) Cumplimiento (de los dos RF, 17 y 18)

**RF17** se cumple **sin excepción**: el comprobante se emite para la firma del cliente antes
de poder confirmar la entrega (el servidor lo exige, no solo la pantalla) y la entrega se
registra como movimiento de caja al confirmar. La única observación (sección 6a) es que
IMPRIMIR el comprobante no exige las 4 condiciones de RF16 — el texto de RF17 no lo pide
tampoco (solo dice "antes de confirmar la entrega", y eso sí se cumple: nunca se puede
CONFIRMAR sin haber impreso), pero es la mejora que Joel ya pidió evaluar.

**RF18** se cumple **sin excepción**: se registran los 3 tipos de movimiento que pide el texto
(entregas del gerente, desembolsos, cobros — más algunos otros tipos que no pide el texto pero
tampoco lo contradicen: rendición, vueltos, compensaciones), se muestra el saldo teórico
siempre actualizado, y se puede reimprimir el comprobante de cada cobro y desembolso (las
entregas del gerente nunca tuvieron uno que reimprimir, consistente con el párrafo anterior).

## 7. Decisiones

No se encontraron hallazgos que exijan programar algo — RF18 cumple el texto firmado tal cual
ya estaba implementado. Confirmado en pantalla el 11/10/2026 (sección 9). Queda 1 mejora menor
propia de RF18 (sección 10, punto b del pedido de Joel), anotada para una próxima vuelta.

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 11/10/2026 | Reprueba del Lote 1 (D.4, hallazgo de Joel): con la fila de "Apertura (fondo inicial)" de arriba, "Todavía no hay movimientos." quedaba confuso (ya hay una fila) — ahora dice "Sin movimientos desde la apertura." cuando la caja está abierta sin movimientos reales; el texto original se mantiene solo para cuando no hay ninguna sesión | *(pendiente)* | — |
| 11/10/2026 | Consolidación RF08-RF19 (Lote 1, B.14): la tabla de Movimientos muestra el fondo inicial como primera fila | *(pendiente)* | — |
| 11/10/2026 | Probado en pantalla completo con CC-2026-00001: movimientos de entrega y desembolso, saldo teórico cuadrando exacto en 0, reimpresión del comprobante | *(pendiente)* | `rf18-probado` |
| 11/10/2026 | Análisis completo contra el texto firmado, sin cambios de código | *(pendiente)* | — |
| 07/11/2026 | **B107** (B86, tramo 3): *"Saldo teórico actual"* pasó a *"Saldo teórico actual (efectivo)"*, y debajo de esa tabla se agregó la aclaración de por qué puede no coincidir con lo que el Panel muestra como cobrado: *"Este saldo es el efectivo. Lo cobrado que figura en el Panel puede ser unos guaraníes mayor, porque incluye el redondeo a favor del cliente."* (dos oraciones, B42). **Esta pantalla es el único dueño de esa explicación** —es donde la cajera cuenta su plata y donde surgió la confusión del 01/11/2026—; en el Panel las etiquetas ya dicen "aplicado a la deuda" (ver RF30). **Solo texto: el saldo teórico se sigue calculando igual** (fondo inicial + movimientos). Probado en pantalla por Joel el mismo día, con la caja abierta (§9) | `baf2fdf` | — |

## 9. Pruebas realizadas en pantalla

**11/10/2026 (Joel), después del Clean and Build, cajera Sofía Martínez, con CC-2026-00001:**
- **Movimientos de la sesión**: "Entrega del gerente +4.000.000" y "Desembolso −5.000.000
  DES-2026-000001" (con su número de comprobante en la columna, y presumiblemente el botón
  "Reimprimir") — confirma la tabla de `listarMovimientosConComprobante` (sección 2/6e).
- **Saldo teórico**: 0 — exactamente `1.000.000 (fondo inicial) + 4.000.000 (entrega) −
  5.000.000 (desembolso)` — confirma la fórmula `sumarMovimientos` (sección 2) cuadrando a la
  cifra exacta, sin ningún desvío.
- **Reimpresión**: confirmada junto con RF17 (sección 9 de `RF17_comprobante_desembolso.md`) —
  "REIMPRESIÓN — 11/10/2026 17:09 — Sofía Martínez" en los dos ejemplares.

**Resultado: RF18 queda probado completo.** 1 mejora menor propia de RF18 (sección 10, punto
b), NO corregida en esta vuelta.

**12/10/2026 (Joel), cajera, CC-2026-00001 (Mirtha), caja abierta a las 08:13 con fondo 0**: el
movimiento `COBRO` (+500.000) y `VUELTO` (−16.850) del adelanto de capital (RF27, ver
`RF27_adelantar_capital.md` §9) quedaron registrados en esa sesión, con saldo teórico resultante
483.150 Gs. — exactamente `0 (fondo) + 500.000 (cobro) − 16.850 (vuelto)`, sin ningún desvío.

## 10. Pendientes y trabajo futuro

b) ~~**Mostrar el fondo inicial como primera fila de "Movimientos"**~~ — **corregido el
   11/10/2026** (consolidación RF08-RF19, Lote 1, B.14): la tabla ahora agrega, siempre primera,
   una fila fija "Apertura (fondo inicial)" con la fecha de apertura y el saldo inicial de la
   sesión (no es una fila de `movimientos_caja`, es un dato de `sesiones_caja` mostrado ahí).
   **Reprobado por Joel (11/10/2026)**: quedaba el texto "Todavía no hay movimientos." debajo de
   la fila de apertura, confuso — corregido (D.4, ver Historial), pendiente de reprueba puntual.
- Entrar como oficial (forzando la URL de `/caja` o de un recibo/comprobante) y confirmar el
  403, tanto en la pantalla como en la reimpresión directa — todavía no probado.
- El comprobante de entrega del gerente (sección 6, hallazgo) — mismo pendiente ya anotado en
  RF15/RF16, no se repite acá.

## 11. Guion para la defensa

**Confirmado en pantalla el 11/10/2026 (sección 9), con CC-2026-00001 — salvo el punto 4
(oficial con 403), todavía no probado.**

1. Mostrar la tabla de movimientos de la sesión abierta, con la entrega del gerente, y (una vez
   confirmado RF17) el desembolso.
2. Mostrar el saldo teórico actualizándose con cada movimiento.
3. Reimprimir el recibo de un cobro y el comprobante de un desembolso desde la misma tabla —
   mostrar que la entrega del gerente no tiene ese botón.
4. Entrar como oficial y mostrar el 403 en `/caja` y en cualquier intento de reimpresión directa.
5. Mostrar el PDF del arqueo de una sesión cerrada con el mismo resumen de movimientos.
