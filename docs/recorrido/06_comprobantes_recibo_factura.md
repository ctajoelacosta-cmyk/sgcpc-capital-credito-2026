# Comprobantes del cobro: cuándo y por qué se emite recibo, factura o constancia (cómo funciona hoy)

Documento de referencia obligatorio antes de tocar la emisión de comprobantes en la fase 2 (ver
`00_LEEME.md`, "Protocolo antes de cualquier cambio"). Complementa la tabla general de
`06_cobro_mapa_completo.md` § 8 con la **regla de decisión exacta** (clase/método/condición en
el código), una tabla por tipo de operación, la numeración completa, los datos fiscales de la
factura, las plantillas, las tablas que se escriben, y varios ejemplos reales ya recorridos en
pantalla — incluida la sección **"Por qué recibo, factura y constancia de cancelación"**, que
explica con un caso real por qué una sola operación puede emitir los tres documentos a la vez.

## 1. La regla de decisión exacta

Los tres documentos (recibo, factura, constancia) se deciden dentro de la **misma transacción**
que confirma el cobro (`ServicioCobranza.confirmarCobro`/`.confirmarAnticipoCapital`/
`.confirmarCancelacionTotal`) — nunca por separado, nunca como un paso posterior.

- **Recibo — SIEMPRE.** Las tres operaciones de cobro toman un número de recibo sin ninguna
  condición (`numeradorDAO.siguienteValor(conexion, "RECIBO", año)`, una vez en cada uno de los
  tres métodos, antes de construir el `Pago`). Haya o no algo gravado, con capital puro o con
  intereses, siempre hay recibo.
- **Factura — SOLO si hay algo gravado.** La condición exacta es un método puro y testeable:

  ```java
  // ServicioCobranza.java, línea 1369
  boolean correspondeEmitirFactura(BigDecimal totalGravado) {
      return totalGravado.compareTo(BigDecimal.ZERO) > 0;
  }
  ```

  `totalGravado` es la suma de **interés compensatorio + moratorio + punitorio, cada uno YA con
  su IVA incluido** (`detalle.getInteresConIva()`, `.getMoratorioConIva()`, `.getPunitorioConIva()`,
  o `liquidacion.getInteresDevengado()+ivaInteresDevengado()` en un adelanto). **El capital
  nunca entra en esa suma** — comentario propio del código (línea 1467): *"D-78: solo conceptos
  gravados (interés, moratorio, punitorio); el capital nunca entra acá."* Si `totalGravado` es
  exactamente 0 (ningún interés devengado, sin mora), `correspondeEmitirFactura` da `false` y
  **no se llama a `emitirFactura`, no se valida el timbrado, no se consume ningún número de
  factura** — el cobro queda con recibo únicamente.
- **Constancia de cancelación — solo al cancelar total.** `ServicioCobranza.
  confirmarCancelacionTotal` siempre asigna un número `CAN-aaaa-nnnnnn`
  (`NumeradorDAO`, clave `CONSTANCIA`) junto con marcar el préstamo `FINALIZADO` — no depende de
  si hubo o no interés gravado (una cancelación con capital puro también tiene su constancia).
- **Ninguno — no existe ese caso dentro de un cobro.** Todo cobro confirmado por pantalla pasa
  siempre, como mínimo, por el recibo; no hay un camino que confirme un cobro sin emitir nada.
  (El comprobante de **desembolso**, en cambio, no es un recibo de cobro — es su propio
  documento, ver § 2.)

## 2. Tabla por tipo de operación

| Operación | Recibo | Factura | Constancia | Notas de montos/numeración |
|---|---|---|---|---|
| **Cobro de 1 cuota vencida** (capital+interés+mora, si corresponde) | Sí, siempre, `REC-aaaa-NNNNNN` | Sí si interés/moratorio/punitorio de esa cuota > 0; si la cuota solo tenía capital pendiente (caso raro), no | — | 1 recibo, 1 factura (o ninguna) por el cobro completo |
| **Cobro de varias cuotas vencidas de una vez** (RF20/RF22: "todo lo vencido + la que vence hoy") | Sí, uno solo para toda la operación | Sí si la suma de interés/moratorio/punitorio de **todas** esas cuotas > 0 — una sola factura con una línea por cuota/concepto (`construirLineasCuota`, una llamada por cada `DetalleCuotaExigible`) | — | Un solo `REC-`/una sola factura para todo el cobro, no uno por cuota |
| **Pago parcial** (no cubre toda la deuda exigible) | Sí | Igual que arriba, sobre lo efectivamente aplicado | — | `pago.tipo = PARCIAL` |
| **Pago de más / vuelto** | Sí (el recibo muestra "Vuelto") | Según haya o no gravado en lo aplicado (el vuelto nunca se factura, no es un concepto) | — | Movimiento `COBRO` por lo **recibido** (nunca por lo aplicado) + movimiento `VUELTO` por la diferencia, ambos en caja — confirmado con un caso real, ver § 7 |
| **Adelanto a capital, SIN interés devengado** (mismo día del desembolso, como el ejemplo de § 7) | Sí | **No** — `totalGravado = 0` | — | Único caso de anticipo sin factura |
| **Adelanto a capital, CON interés devengado** (días ya transcurridos del período) | Sí | Sí — una línea de "interés del período en curso" (`liquidacion.getInteresDevengado()+IVA`) | — | Ejemplo real ya visto en RF27 (Mirtha, 2.286 Gs. + IVA 229 Gs., ver § 7 de `RF23_recibo_factura.md`) |
| **Cancelación total anticipada** | Sí | Sí si queda interés devengado del período en curso > 0; si la cancelación es el mismo día del desembolso (interés 0), no | Sí, **siempre** (`CAN-aaaa-nnnnnn`), haya o no factura | El préstamo pasa a `FINALIZADO` en la misma transacción |
| **Reversión/anulación de un cobro — paso 1, solicitud (cajera)** | **Nada cambia todavía** — el recibo original sigue exactamente igual, sin ninguna marca; `pagos.estado_pago` sigue `CONFIRMADO` | Sin cambios | Sin cambios | Ningún comprobante nuevo ni movimiento de caja — solo una fila `SOLICITADA` en `reversiones_pago` (ver § 7, caso real) |
| **Reversión/anulación de un cobro — paso 2, autorización (gerente)** | El recibo original queda, pero se **regenera** con la franja roja "ANULADO POR REVERSIÓN" (mismo número) | Si tenía factura, se marca `ANULADA` y se emite una **Nota de anulación** (`NAN-aaaa-NNNNNN`, clave `ANULACION`) — nunca se reemite ni se corrige la factura, nunca una nota de crédito | Se genera una **Constancia de reversión** (`REV-aaaa-NNNNNN`, ver § 3) | Un movimiento `COMPENSACION` (signo −1) **por el monto `aplicado` del pago, nunca por el `recibido`** — si hubo vuelto, no se "revierte" por separado (ver § 7, caso real, C31) |
| **Reversión/anulación de un cobro — paso 2 alternativo, rechazo (gerente)** | **Nada cambia** — el recibo sigue exactamente igual que antes, nunca se regenera | Sin cambios | **Ningún comprobante** — ni constancia, ni nota de anulación; la pantalla de éxito ("Solicitud de reversión rechazada") no ofrece nada para imprimir | Ningún movimiento de caja — solo `reversiones_pago.estado = 'RECHAZADA'` con el motivo del gerente (ver § 7, séptimo ejemplo) |
| **Desembolso** (no es un cobro) | No — no hay `Pago` de por medio | No | No | Comprobante propio, `DES-aaaa-NNNNNN` (clave `DESEMBOLSO`), ver § 5 |

## 3. Numeración

| Documento | Clave en `numeradores` | Formato | Correlatividad | Qué pasa en una reversión |
|---|---|---|---|---|
| **Recibo** | `RECIBO` | `REC-aaaa-NNNNNN` | Por **año calendario** del cobro | No se reemite ni se anula un número nuevo — el mismo PDF se **regenera** con la franja "ANULADO POR REVERSIÓN" |
| **Factura** | `FACTURA` | `establecimiento-puntoExpedicion-numero` (p. ej. `001-001-0000001`, `Factura.getNumeroCompleto()`) | Por la **vigencia del timbrado** (`fiscal.timbrado_vigencia_desde`), no por año calendario — sigue sin reiniciarse mientras el timbrado no cambie | `FacturaDAO.marcarAnulada`: `estado = 'ANULADA'`, nunca `DELETE`; se emite una **Nota de anulación** con su propio número |
| **Nota de anulación** | `ANULACION` | `NAN-aaaa-NNNNNN` | Por año calendario de la reversión | Documento nuevo, propio de la reversión — no existe fuera de ese contexto |
| **Constancia de cancelación** | `CONSTANCIA` | `CAN-aaaa-nnnnnn` | Por año calendario | — (una cancelación total, una vez autorizada, no se revierte en este sistema; no hay caso documentado) |
| **Constancia de reversión** | *(sin numerador propio)* | `REV-aaaa-NNNNNN`, usa directamente `id_reversion_pago` (la PK) como secuencia | Ya es única por construcción (PK de `reversiones_pago`) | Es, ella misma, el documento que se emite al autorizar la reversión |
| **Constancia de devolución del pagaré** | `DEVOLUCION` | `DEV-aaaa-nnnnnn` | Por año calendario | No aplica (cierre documental, RF29, posterior a la cancelación) |
| **Comprobante de desembolso** | `DESEMBOLSO` | `DES-aaaa-NNNNNN` | Por año calendario | No aplica (no es un cobro) |

**Cómo se garantiza "sin saltos"**: `NumeradorDAO.siguienteValor` hace `SELECT ... FOR UPDATE`
sobre la fila `(clave, año)` antes de incrementarla — bloqueo real de fila, así que dos cobros
simultáneos nunca pueden tomar el mismo número ni dejar un hueco. Para la factura, antes de
tomar el número también se valida que el timbrado esté vigente en la fecha del cobro
(`validarTimbradoVigente`) y que no esté agotado (`fiscal.factura_numero_hasta`) — si falla,
**se rechaza todo el cobro**, no solo la factura, sin haber consumido ningún número.

**Estado real verificado el 23/10/2026 con `SELECT` en `sgcpc_db_pruebas`**: la tabla
`numeradores` solo tiene fila para las claves que alguna vez se usaron — ese día: `RECIBO/2026 =
1`, `DESEMBOLSO/2026 = 2`, `OPERACION/2026 = 2`, `SOLICITUD/2026 = 2`, `AUTORIZACION/2026 = 2`.
**No existía ninguna fila `FACTURA` ni `CONSTANCIA`** — nunca se había emitido una factura ni
una constancia en esta base (0 filas en `facturas`); consistente con que el único pago hasta
ese día (`REC-2026-000001`) fue un anticipo sin interés devengado (§ 7).

**Actualizado el 25/10/2026**: tras la cancelación total de `CC-2026-00002` (ver "Por qué
recibo, factura y constancia de cancelación", más abajo), `numeradores` ya tiene `RECIBO/2026 =
3`, `FACTURA/2026 = 1` y `CONSTANCIA/2026 = 1` — la primera factura y la primera constancia de
esta base, ambas de ese mismo pago.

## 4. Datos fiscales de la factura

Verificado en `factura.jrxml` y en `ServicioCobranza` (bloque `emitirFactura`/`generarPdfFactura`):

- **De la empresa**: razón social, RUC, domicilio, teléfono/WhatsApp, correo; **RUC: [...]**,
  **TIMBRADO N.º [...]**, **Vigencia: [desde] al [hasta]**, **FACTURA N.º [completo]**.
- **Del cliente**: nombre o razón social, **RUC / C.I.**, dirección (sin la referencia coloquial
  del domicilio — corregido en `ServicioCobranza.java:2085` con `domicilioSinReferencia`, mismo
  criterio que otros comprobantes legales), identificador de la operación.
- **Condición de venta**: "CONTADO ☒ CRÉDITO ☐" — fijo, siempre contado (no hay facturación a
  crédito en este negocio).
- **Conceptos gravados al 10 %, el capital nunca aparece**: `Factura.totalExenta` siempre `0`
  (no hay ningún concepto exento declarado — RN-25 dice que el capital "se documenta en el
  recibo, no en la factura", así que ni siquiera figura como línea exenta); `totalGravada10` es
  exactamente la suma de las líneas de interés/moratorio/punitorio (ya con IVA incluido); no se
  usa la columna "Gravada 5 %" (el parámetro `TOTAL_GRAVADA_5`/`IVA_5` existe en la plantilla
  pero este negocio no tiene conceptos al 5 %, siempre en 0).
- **Cálculo del IVA — 10 % sobre cada concepto por separado**:

  ```java
  // ServicioMotorFinanciero.java, líneas 42/61-63
  private static final BigDecimal TASA_IVA = new BigDecimal("0.10");
  static BigDecimal iva(BigDecimal base) {
      return redondear(base.multiply(TASA_IVA));   // HALF_UP, 0 decimales (Gs.)
  }
  ```

  El IVA se calcula **por separado sobre el interés compensatorio, sobre el moratorio y sobre el
  punitorio** (nunca sobre la suma de los tres, y nunca sobre el capital). Cada resultado se
  redondea independientemente (`HALF_UP` a entero, porque el guaraní no tiene decimales) antes
  de sumarse al total.
- **Dato que existe hoy, aunque no debería (observación para § 9)**: `TASA_IVA = 0.10` está
  **escrita como constante en Java** (`ServicioMotorFinanciero`), no como fila de
  `parametros_sistema` — a diferencia de todos los demás datos fiscales (`fiscal.timbrado_numero`,
  `fiscal.establecimiento`, `fiscal.punto_expedicion`, etc.), que sí son parámetros editables sin
  tocar código.
- **Lo que existe hoy de la parte legal/DNIT**: autoimpresor con timbrado (`fiscal.tipo_comprobante
  = AUTOIMPRESOR`, decisión documentada desde el anteproyecto, Doc. 00 línea 126, riesgo R-03 —
  "SIFEN en V2", no una omisión). **Lo que no existe**: línea de firma y aclaración del emisor en
  `factura.jrxml` (ya anotado en `RF23_recibo_factura.md` § 10, pendiente de revisión por un
  contador/abogado antes de usarse con clientes reales — **no se verifica ni se opina sobre la
  normativa acá**, solo se registra que la revisión legal sigue pendiente).
- **Redondeo**: todos los montos de la factura son enteros en guaraníes (`NUMERIC(18,0)`); no
  hay centavos ni fracciones que redondear más allá del `HALF_UP` del cálculo del IVA.

## 5. Plantillas (.jrxml) y criterio de original/copia

| Comprobante | Plantilla | Datos: de dónde salen | Original/copia |
|---|---|---|---|
| **Recibo** | `recibo.jrxml` | Todo calculado y formateado en `ServicioCobranza.generarPdfRecibo` antes de llamar a JasperReports — el reporte "no calcula ni recalcula nada" (comentario propio del archivo) | 1 página, texto fijo **"ORIGINAL: Cliente / COPIA: Capital Crédito"** |
| **Factura** | `factura.jrxml` | `ServicioCobranza.generarPdfFactura`/`emitirFactura` (líneas desde `construirLineasCuota`, totales desde `calcularTotalesFactura`) | 1 página, texto fijo **"ORIGINAL: Cliente / DUPLICADO: Archivo tributario"** — tercera variante de palabras, distinta de la del recibo |
| **Constancia de cancelación** | `constancia_cancelacion.jrxml` (+ `encabezado_constancia.jrxml` compartido) | `ServicioCobranza.obtenerPdfConstancia`/`generarPdfConstancia` | **Sin ninguna leyenda de original/copia** |
| **Constancia de reversión** | `constancia_reversion.jrxml` | `ServicioCobranza.generarPdfConstanciaReversion` | **Sin ninguna leyenda de original/copia** |
| **Constancia de devolución** | `constancia_devolucion.jrxml` | `ServicioFormalizacion` (RF29) | **Sin ninguna leyenda de original/copia** |
| **Nota de anulación** | `nota_anulacion.jrxml` | `ServicioCobranza.generarPdfNotaAnulacion` | **Sin ninguna leyenda de original/copia** |
| **Comprobante de desembolso** | `comprobante_desembolso.jrxml` | `ServicioDesembolso.generarComprobantePreDesembolso` | **2 páginas reales** (`title`=**"ORIGINAL – Empresa"**, `summary`=**"COPIA – Cliente"**), además con la marca de agua/texto "REIMPRESIÓN" en cualquier impresión después de la primera (único comprobante que distingue visualmente la reimpresión, RF17) |

**Sobre el hallazgo B58** (criterio de original/copia): con los 7 comprobantes revisados, hay
**tres palabras distintas** para lo mismo ("COPIA" en el recibo, "DUPLICADO" en la factura,
"COPIA" otra vez pero con dueño invertido en el desembolso) y **cuatro comprobantes que no dicen
nada** al respecto (las tres constancias y la nota de anulación). No es solo una inconsistencia
de palabras — es una inconsistencia de **si el documento declara o no quién se queda con cuál
copia**.

## 6. Tablas que se escriben y relación pago–recibo–factura

- **`pagos`**: una fila por cada cobro confirmado (`numero_recibo` único, `tipo` ∈ `NORMAL`,
  `PARCIAL`, `CANCELACION`, `ANTICIPO`; `id_archivo_recibo` referencia el PDF ya guardado).
  **El recibo vive adentro de `pagos`** — no hay una tabla `recibos` separada; el "recibo" es,
  en los datos, simplemente `pagos.numero_recibo` + el PDF cacheado.
- **`facturas`**: 0 o 1 fila por `pagos.id_pago` (`UNIQUE` implícito por el flujo, nunca más de
  una factura por pago), con `timbrado`/`establecimiento`/`punto_expedicion`/`numero` (único
  combinado), `estado` `EMITIDA`/`ANULADA`, `id_archivo` (PDF cacheado), y, si se anuló,
  `id_reversion_pago`/`numero_nota_anulacion`/`id_archivo_nota_anulacion`.
- **`aplicaciones_pago`**: el detalle real de qué se cobró (capital/interés/moratorio/
  punitorio/tributos por cuota o por el anticipo) — **tanto el recibo como la factura se derivan
  de esta tabla al reimprimir** (`reconstruirLineasFactura`/`reconstruirDetalleRecibo`), nunca
  guardan sus propias líneas por separado (D-93: "las líneas de una factura ya emitida se
  derivan, nunca se guardaron").
- **`liquidaciones_anticipadas`**: una fila por anticipo/cancelación, con el desglose del
  interés devengado, y `numero_constancia` cuando es una cancelación total.
- **`movimientos_caja`**: `COBRO` (signo +1) y, si hubo vuelto, `VUELTO` (signo −1) — ver
  `06_primer_cobro.md` § Paso 5 y el hallazgo **B60** sobre que esta tabla no distingue
  "anticipo" de "cobro normal" en su propio `tipo`.
- **`auditoria_operaciones`**: un único evento por operación (`COBRAR`/`CANCELAR_TOTAL`/
  `ANTICIPAR_CAPITAL`, entidad `PAGO`) — **no hay un evento propio para "emitir recibo" ni
  "emitir factura"**: ambos quedan implícitos dentro de ese mismo evento, ya con los dos
  documentos generados antes del `commit`. La reversión sí tiene sus propios eventos
  (`SOLICITAR_REVERSION`, `AUTORIZAR_REVERSION`/`RECHAZAR_REVERSION`), y el detalle de
  `AUTORIZAR_REVERSION` sí menciona el número de la nota de anulación, si hubo.
- **Qué NO se audita**: la **reimpresión** de un recibo, una factura, una constancia o una nota
  de anulación ya emitidos — verificado en `obtenerOGenerarPdf` (líneas ~1780-1810): el `idUsuario`
  que recibe el método solo se usa para `archivo.setCargadoPor(idUsuario)` la primera vez que se
  genera el PDF; una reimpresión posterior no llama a `ServicioAuditoria.registrar` en ningún
  punto. **Contraste**: el comprobante de **desembolso** sí tiene un evento de auditoría propio
  para esto, `REIMPRIMIR_COMPROBANTE_DESEMBOLSO` (RF17 § 3) — es decir, el mismo sistema audita
  la reimpresión de un comprobante (desembolso) y no audita la de los otros cinco (recibo,
  factura, constancia de cancelación, constancia de reversión, nota de anulación).

## 7. Ejemplo real de este recorrido: el anticipo de CC-2026-00002 (23/10/2026)

Tras confirmar el anticipo a capital de `CC-2026-00002` (193.250 Gs., interés devengado 0 Gs. —
ver `06_primer_cobro.md` § Paso 3), "Imprimir recibo" abre un PDF de una sola página, **"RECIBO
DE DINERO N.º REC-2026-000001"**, con:

- Encabezado de la empresa (escudo, razón social, RUC, domicilio, teléfono/WhatsApp y correo
  electrónico).
- "Asunción, [fecha y hora]"; "Operación: [identificador]"; "Cliente: [nombre] C.I.: [cédula]".
- **"Factura asociada: no corresponde (sin conceptos gravados)."**
- "Anticipo de capital: 193.250 Gs. – Interés del período en curso: 0 Gs. – IVA: 0 Gs."
- "Resumen del plan regenerado: 11 cuotas restantes, nuevo último vencimiento 23/09/2027.
  Próxima cuota (IVA incluido): 23/11/2026 por 192.891 Gs. Saldo de capital después del pago:
  1.806.750 Gs."
- "Efectivo recibido: 193.250 Gs. Vuelto: 0 Gs. Aplicado: 193.250 Gs." y "Son:" en letras.
- Firmas de la cajera (con nombre) y del cliente.
- "ORIGINAL: Cliente                    COPIA: Capital Crédito".
- "Emitido por: [usuario] — [fecha de emisión]" y "Página 1 de 1".

**La cadena exacta de decisiones del código que llevó a este resultado**:

1. `confirmarAnticipoCapital` calcula `liquidacion = calcularAnticipoParcial(...)` — con la fecha
   de hoy igual a la fecha de desembolso, `periodoActual` devuelve `díasTranscurridos = 0`
   (ver `06_primer_cobro.md` § Paso 2), así que `liquidacion.getInteresDevengado() = 0` y
   `getIvaInteresDevengado() = 0`.
2. `totalGravado = liquidacion.getInteresDevengado().add(liquidacion.getIvaInteresDevengado())`
   → `totalGravado = 0`.
3. `correspondeEmitirFactura(totalGravado)` → `0.compareTo(0) > 0` → **`false`**.
4. Como es `false`: **no** se llama a `validarTimbradoVigente`, **no** se arma ninguna
   `LineaFactura`, **no** se llama a `emitirFactura`, **no** se consume ningún número de la
   clave `FACTURA` — confirmado con el `SELECT` de § 3 (no existe fila `FACTURA` en
   `numeradores`, 0 filas en `facturas`).
5. El número de recibo sí se toma siempre, sin depender de lo anterior: `numeradorDAO.
   siguienteValor(conexion, "RECIBO", 2026)` → `1` → `REC-2026-000001`.
6. `generarPdfRecibo` recibe `factura = facturaDAO.obtenerPorPago(...) = null` → `TIENE_FACTURA
   = false` → el recibo imprime "Factura asociada: no corresponde (sin conceptos gravados)."

Un único cobro con intereses reales (sin interés devengado = 0) sí llegó a emitir factura en
otra prueba de este proyecto: el 12/10/2026, con Mirtha (`CC-2026-00001`, adelanto de capital con
interés devengado 2.286 Gs. + IVA 229 Gs. → `REC-2026-000001`/`001-001-0000001` de aquella base
de pruebas, ya documentado en `RF23_recibo_factura.md` § 9) — queda como referencia de cómo se
ve este mismo camino cuando `totalGravado > 0`.

### Segundo ejemplo real: pago de más con vuelto (REC-2026-000002, 23/10/2026)

Un segundo anticipo de capital sobre el mismo `CC-2026-00002` (próxima cuota 192.891 Gs., en
efectivo 192.850 Gs.), con la cajera recibiendo **200.000 Gs.** en vez de los 192.850 Gs.
propuestos. "Imprimir recibo" abre un PDF de una sola página, **"RECIBO DE DINERO N.º
REC-2026-000002"**, con:

- "Asunción, 23/10/2026 13:23"; "Operación: [identificador]"; "Cliente: [nombre] C.I.:
  [cédula]".
- **"Factura asociada: no corresponde (sin conceptos gravados)."** (mismo motivo que el primer
  ejemplo: interés devengado 0).
- "Anticipo de capital: 192.850 Gs. – Interés del período en curso: 0 Gs. – IVA: 0 Gs."
- "Resumen del plan regenerado: **10 cuotas restantes, nuevo último vencimiento 23/08/2027.**
  Próxima cuota (IVA incluido): 23/11/2026 por 192.503 Gs. Saldo de capital después del pago:
  1.613.900 Gs."
- **"Efectivo recibido: 200.000 Gs. Vuelto: 7.150 Gs. Aplicado: 192.850 Gs."**
- **"Son:" en letras, por el monto APLICADO (192.850 Gs.), no por lo recibido (200.000 Gs.)**
  — el campo describe lo que de verdad bajó la deuda, nunca el efectivo que pasó por el
  mostrador.
- Firmas de la cajera (con nombre) y del cliente.
- "ORIGINAL: Cliente                    COPIA: Capital Crédito".
- "Emitido por: [usuario] — [fecha de emisión]" y "Página 1 de 1".

**Dato a anotar — el "último vencimiento" retrocede un mes por cada anticipo de una cuota,
mientras la próxima cuota sigue en la misma fecha**: el primer recibo (`REC-2026-000001`) decía
"nuevo último vencimiento **23/09/2027**" (11 cuotas); este segundo recibo dice **23/08/2027**
(10 cuotas) — un mes antes, una cuota menos. En los dos casos la **próxima cuota sigue siendo
el 23/11/2026** (ver C23, `06_primer_cobro.md`) — adelantar capital nunca mueve la próxima
cuota hacia adelante, siempre recorta el final del plan.

**Verificado con `SELECT` en `sgcpc_db_pruebas`**:

- **`pagos`** (id 2): `monto_recibido` = 200.000, `monto_vuelto` = 7.150, `monto_aplicado` =
  192.850, `monto_redondeo` = 0 (sigue sin aplicar para `ANTICIPO`, igual que el primer ejemplo).
- **`movimientos_caja`** de la sesión N.º 4: **dos filas**, no una sola neta — `COBRO` +200.000
  (signo +1) y `VUELTO` −7.150 (signo −1), ambas con `referencia_tipo = 'PAGO'` apuntando al
  mismo pago. El dinero que entra a la caja es lo **recibido completo**; el vuelto sale como un
  movimiento propio, no un ajuste del mismo `COBRO`.
- **Dónde suma en el arqueo**: el `COBRO` (con `pagos.tipo = ANTICIPO`) entra en **"Anticipos de
  capital"**; el `VUELTO` entra en **"Vueltos entregados"** — dos renglones distintos del
  RESUMEN (ver `06_cobro_mapa_completo.md` § 8 y `07_cierre_del_dia.md` § Paso 1 para el formato
  exacto del arqueo).

**El monto que de verdad se aplica no es libre — está limitado por el selector "1/2/3
cuotas"**: verificado en `ControladorCobranzaServlet.procesarAnticipar` —

```java
BigDecimal montoAplicado = montoRecibido;
if (montoSugeridoTexto != null && !montoSugeridoTexto.trim().isEmpty()) {
    montoAplicado = montoRecibido.min(parsearMonto(montoSugeridoTexto));
}
```

`montoSugerido` es siempre uno de los tres valores fijos del selector (1×, 2× o 3× la próxima
cuota, ya redondeada hacia abajo) — nunca un monto intermedio que la cajera escriba a propósito.
El monto aplicado es, siempre, **el menor entre lo recibido y ese valor fijo**: si recibe más
que el valor elegido (como en este ejemplo, 200.000 contra 192.850), el excedente se devuelve
como vuelto; **no hay manera de aplicar, por ejemplo, 195.000 Gs. exactos** si ese número no
coincide con ninguno de los tres botones — solo "quedarse corto" (aceptar el tope del botón, con
vuelto) o "pasarse" al siguiente botón (2 o 3 cuotas, un salto mucho mayor). **Si "Recibido" es
menor que el monto sugerido**: `montoAplicado = montoRecibido` (el menor de los dos, en este
caso lo recibido) — se aplica todo lo recibido a capital, sin vuelto, siempre que alcance al
menos para el interés+IVA del período (si no, `confirmarAnticipoCapital` rechaza con "El monto
no alcanza a cubrir siquiera el interés del período en curso...", D-90/D-88). **No existe ningún
concepto de "saldo a favor" del cliente** — revisado el esquema (`bd/01_esquema.sql`): no hay
ninguna tabla ni columna para un crédito o saldo a favor retenido para un pago futuro; lo único
que puede volver al cliente es efectivo, como vuelto, en el momento. Ver el hallazgo **C28**
(`06_primer_cobro.md` § Hallazgos del Paso 6) para la discusión completa de esta limitación, de
cara al rediseño del cobro en la fase 2.

### Tercer ejemplo real: reversión de un cobro, paso de solicitud (23/10/2026)

La cajera solicitó la reversión de `REC-2026-000002` (el anticipo con vuelto), con el motivo
*"Error de la cajera: se registró un anticipo no solicitado por el cliente."* — confirma lo que
dice la tabla de § 2: **mientras la solicitud está `SOLICITADA`, ningún comprobante cambia.**

**Verificado con `SELECT` en `sgcpc_db_pruebas`**: el recibo original (`REC-2026-000001`, el
pago id 1) ni siquiera está involucrado; el pago de la reversión (id 2) sigue con
`estado_pago = 'CONFIRMADO'` — **no** `REVERTIDO` — y no hay ninguna fila nueva en `facturas`,
`archivos_binarios` ni `movimientos_caja` por esta sola solicitud. La única fila nueva es en
`reversiones_pago` (`estado = 'SOLICITADA'`, con el motivo, quién solicitó y cuándo) y el único
evento de auditoría es `SOLICITAR_REVERSION`. **El recibo `REC-2026-000002`, si se reimprime
ahora, sale exactamente igual que antes de solicitar la reversión** — sin ninguna franja ni
aviso — porque esa franja ("ANULADO POR REVERSIÓN") solo aparece cuando el pago pasa a
`REVERTIDO`, y eso recién ocurre si el gerente **autoriza** la solicitud (ver § 2, fila "paso
2"). Ver el desarrollo completo de esta solicitud, incluida la regla exacta de qué cobros se
pueden revertir (C29) y el riesgo de operar sobre el mismo préstamo mientras está pendiente
(C30), en `06_primer_cobro.md` § Paso 8.

### Cuarto caso: Autorizar/Rechazar, y cuánto efectivo vuelve en una reversión con vuelto

Verificado en `ServicioReversiones.autorizarReversion`/`.rechazarReversion` (código, pantalla
`/reversiones`, ver `06_primer_cobro.md` § Paso 10 para el desarrollo completo):

- **"Autorizar"**: pide confirmación (*"¿Autorizar la reversión del cobro [...] por [...]? No
  se puede deshacer."*), pero **no recibe ningún motivo/observación del gerente** — solo la
  casilla de "factura en poder del cliente" cuando corresponde (ver **B70**). Repite las
  validaciones de "última operación", caja abierta de hoy con efectivo suficiente, y cierre
  documental no completado.
- **"Rechazar"**: pide confirmación y **exige un motivo no vacío** (sin mínimo de caracteres) —
  no toca el pago, la caja ni el plan; el pago queda `CONFIRMADO` tal como estaba.
- **El monto que vuelve en efectivo, si el cobro tenía vuelto**: el movimiento `COMPENSACION`
  usa `pago.getMontoAplicado()`, **nunca** `monto_recibido`. Con el caso real de
  `REC-2026-000002` (recibido 200.000, vuelto 7.150, aplicado 192.850): la `COMPENSACION` sería
  de **192.850 Gs.**, no de 200.000 ni de 207.150. Matemáticamente correcto — el saldo teórico
  de la caja volvería exactamente al que tenía antes del cobro (100.000 Gs., el fondo de la
  sesión N.º 4), porque el vuelto de 7.150 Gs. ya había salido físicamente de la caja en el
  momento del cobro original y no hay nada más que devolver por ese concepto. Ver **C31**.

**Confirmado en pantalla y con `SELECT` (23/10/2026, 14:42, `06_primer_cobro.md` § Paso 11)**:
se autorizó la reversión real de `REC-2026-000002` — éxito *"Se devolvieron 192.850 Gs. de la
caja."*; `pagos.estado_pago` pasó a `REVERTIDO`; el plan de la versión 2 (el del primer
anticipo, 11 cuotas, cuota 1 por 192.891 Gs.) volvió a `vigente = true`; la `COMPENSACION`
(192.850, signo −1) quedó en la sesión de caja de la **cajera** con `registrado_por` del
**gerente**; saldo teórico resultante 100.000 Gs., exacto. **0 filas en `facturas`** — ninguna
Nota de anulación, correctamente, porque este pago nunca tuvo factura.

**Nombres, verificado sin hallazgo**: "Imprimir constancia de reversión" genera la
**"CONSTANCIA DE REVERSIÓN DE COBRO"** (`constancia_reversion.jrxml`, `REV-2026-000001`) — un
documento **distinto** de la "Nota de anulación" (`nota_anulacion.jrxml`, clave `ANULACION`),
no el mismo documento con dos nombres. El comentario propio de `constancia_reversion.jrxml` lo
dice explícito: *"Documento interno (no fiscal, a diferencia de nota_anulacion.jrxml)"* — la
constancia se emite siempre que se autoriza una reversión (haya o no factura); la nota de
anulación, solo si el pago original tenía factura. Revisada toda esta documentación: no se
encontró ninguna confusión entre los dos nombres, ni en el código ni en lo ya escrito acá.

### Quinto ejemplo real: la Constancia de reversión impresa (REV-2026-000001, 23/10/2026)

"Imprimir constancia de reversión" abrió un PDF, **"CONSTANCIA DE REVERSIÓN DE COBRO — N.º
REV-2026-000001"**:

- Encabezado de la empresa, con letra visiblemente más chica que el resto de los comprobantes
  (ver B72 más abajo).
- "Asunción, 23 de octubre de 2026".
- *"Por la presente, Capital Crédito Paraguay S.R.L. deja constancia de la devolución en
  efectivo a [cliente], C.I. N.º [...], por la reversión del cobro N.º REC-2026-000002 de la
  operación CC-2026-00002."*
- **"Son:"** en letras, por 192.850 Gs.
- **"DATOS DE LA REVERSIÓN"**: Recibo anulado `REC-2026-000002`; Monto devuelto en efectivo
  192.850 Gs.; Motivo de la solicitud; Solicitó (cajera); Autorizó (gerente); Fecha y hora de
  la autorización 23/10/2026 14:42.
- Leyenda: *"Adjuntar a esta constancia el recibo original y, si corresponde, el original y
  todas las copias de la factura anulada."*
- Firmas de **cajera, gerente y cliente**, con el cliente firmando **"Recibí conforme la
  devolución de 192.850 Gs."**

**La numeración `REV-aaaa-NNNNNN` confirmada en pantalla**: tal cual § 3 ya documentaba, usa
`id_reversion_pago` directamente como secuencia, sin numerador propio en la tabla `numeradores`
— esta es la primera reversión de la base, por eso `REV-2026-000001` (id_reversion_pago = 1),
coincidiendo exactamente con la PK.

**Verificado en el código — responde B72 (¿tiene pie "Emitido por"/"Página"?)**: sí, los
tiene — `constancia_reversion.jrxml` imprime, en su `pageFooter`, el mismo *"Emitido por:
[...] — [...]"* y *"Página [N] de [total]"* que recibo/factura/comprobante de desembolso. Lo
que sí cambia es el encabezado: las 3 constancias (cancelación, reversión, devolución)
comparten el subreporte `encabezado_constancia.jrxml`, que imprime los datos de la empresa en
**tamaño de letra 8**, mientras que recibo/factura/comprobante de desembolso usan **tamaño 9**
en su propio encabezado — una diferencia real de formato, consistente en las 3 constancias,
sin relación con el contenido (no falta ningún dato ni pie).

**C32, actualizado con este documento**: la firma del cliente, **"Recibí conforme la
devolución de 192.850 Gs."**, junto con las de la cajera y el gerente, es precisamente el
control de la entrega física que antes de ver este PDF parecía faltar — queda cubierto, pero
en papel, fuera del sistema. Lo único que sigue sin cubrir es el aviso dentro del sistema a la
cajera en el momento de autorizar. Ver el desarrollo completo en `06_primer_cobro.md` § Paso 11.

### Sexto ejemplo real: reimprimir el recibo ya anulado (REC-2026-000002, 23/10/2026)

"Reimprimir" de `REC-2026-000002` desde `/caja` generó el recibo con:

- **Franja roja grande "ANULADO POR REVERSIÓN"**, con el texto *"Este cobro fue revertido el
  23/10/2026 14:42 (autorizó: [gerente]). Motivo: [motivo] — Este documento NO es válido como
  comprobante de pago."*
- **Marca de agua "ANULADO" en diagonal** sobre todo el cuerpo del documento.

**Confirma en pantalla, exactamente, lo ya verificado en el código** (§ "Cuarto caso" y
`06_primer_cobro.md` § Paso 13): `ServicioCobranza.obtenerPdfRecibo` nunca devuelve el PDF
cacheado para un pago `REVERTIDO` — siempre lo regenera con `REVERTIDO = true`, leyendo fecha,
autorizador y motivo en vivo de `reversiones_pago`. **C33 queda confirmado también en
pantalla**, no solo en el código.

**Dato (sin hallazgo): el "Resumen del plan regenerado" de este recibo quedó congelado en el
momento de la emisión original — "10 cuotas restantes, próxima cuota 192.503 Gs."** — y no
refleja el plan ya restaurado por la reversión (11 cuotas, próxima 192.891 Gs., ver
`06_primer_cobro.md` § Paso 11). Coherente con que el recibo **nunca recalcula nada al
reimprimir** (mismo criterio ya citado en § 1 para cualquier recibo): un comprobante anulado
muestra lo que decía en su momento, no el estado actual del préstamo.

### Séptimo ejemplo real: una reversión rechazada no emite ningún comprobante (23/10/2026)

Sobre el primer anticipo (`REC-2026-000001`, 193.250 Gs.), la cajera solicitó una segunda
reversión; el gerente, esta vez, tocó **"Rechazar"** (con el motivo *"Revisado con el cliente:
el anticipo sí fue solicitado; el cobro es correcto."*) en vez de "Autorizar". El éxito
**"Solicitud de reversión rechazada"** **no ofrece nada para imprimir** — ni constancia, ni
nota de anulación, ni ningún otro documento.

**Verificado con `SELECT` en `sgcpc_db_pruebas`**: `pagos.estado_pago` del pago 1 **sigue
`CONFIRMADO`** (no se tocó); la fila de `reversiones_pago` (id 2) quedó en `estado =
'RECHAZADA'`, con `motivo_rechazo` guardado y **`autorizado_por`/`procesado_por` vacíos** —
nunca se llenan en un rechazo. **0 comprobantes nuevos**: ni un número de `REV-`, ni de
`NAN-`, ni ningún archivo en `archivos_binarios` — un rechazo, a diferencia de una
autorización, no dispara ninguna emisión. El recibo original (`REC-2026-000001`) sigue
exactamente igual que antes, sin ninguna franja ni marca.

**Confirma la tabla de § 2**: la fila "paso 2 alternativo, rechazo" ya anticipaba que un
rechazo no deja ningún rastro documental — este es el caso real que lo confirma.

**Cierre de los siete ejemplos de este documento**: entre el segundo ejemplo (pago de más con
vuelto) y estos cinco de reversión (solicitud, autorización, constancia, reimpresión del
anulado y rechazo), quedó recorrido en pantalla, de punta a punta, el ciclo completo de
comprobantes de un cobro problemático. Ver el resumen de todo el circuito, con sus hallazgos
C29-C34 y B69-B72, en `06_primer_cobro.md` § "Recorrido completo de la reversión" y § Paso 15.

## Por qué recibo, factura y constancia de cancelación (caso real 25/10/2026)

**Pedido explícito de Joel: dejar documentado por qué una cancelación total emite tres
documentos a la vez.** Caso real, recorrido en pantalla el 25/10/2026: cancelación total de
`CC-2026-00002` (saldo de capital 1.806.750 Gs.), con el recibido redondeado y un interés real
de 2 días devengado. Verificado con `SELECT` en `sgcpc_db_pruebas` y leyendo el código.

### 1) Recibo `REC-2026-000003`

**Qué acredita**: el dinero que entró y salió de la caja, no la deuda en sí. El recibo (`ES_
CANCELACION = true` en `recibo.jrxml`) imprime:

- "Efectivo recibido: 1.810.000 Gs. Vuelto: 700 Gs. Aplicado: 1.809.330 Gs. Redondeo: -30 Gs."
  (el redondeo se muestra porque `TIENE_REDONDEO = true` — hubo un centavo perdonado, ver § 5).
- "Son:" en letras, por el monto **aplicado** (1.809.330 Gs.), nunca por lo recibido.
- La leyenda fija **"PRÉSTAMO CANCELADO"** (`staticText` propio, visible solo cuando
  `ES_CANCELACION`).
- **"Factura asociada: 001-001-0000001"** — a diferencia de los dos anticipos del 23/10/2026
  (que decían "no corresponde"), acá **sí** hay un número real, porque `TIENE_FACTURA = (factura
  != null)` y esta vez la factura existe (ver punto 2).

**Regla del código que lo emite siempre**: dentro de `ServicioCobranza.
confirmarCancelacionTotal` (línea ~952), exactamente la misma línea que ya usan `confirmarCobro`
y `confirmarAnticipoCapital` — sin ninguna condición:

```java
int anio = fecha.getYear();
long numeroReciboSecuencia = numeradorDAO.siguienteValor(conexion, "RECIBO", anio);
String numeroRecibo = String.format("REC-%d-%06d", anio, numeroReciboSecuencia);
```

Se ejecuta antes de saber siquiera si va a haber factura — el recibo nunca depende de nada más.

#### Lo observado en pantalla (25/10/2026, 11:20): el recibo completo, con el desglose por cuota

**"RECIBO DE DINERO N.º REC-2026-000003"**: "Asunción, 25/10/2026 10:55"; Operación; Cliente y
C.I.; **"Factura asociada: 001-001-0000001"**. Tabla **Cuota / Capital / Interés / Morat. /
Punit. / IVA / Total**, las 11 cuotas del plan vigente, de 1/11 a 11/11:

- **Cuota 1/11**: capital 152.908, interés 2.345, morat. 0, punit. 0, IVA 235, **total
  155.488**.
- **Cuotas 2/11 a 11/11**: solo capital (155.984 ... 131.547), interés/morat./punit./IVA en 0
  cada una.
- **TOTAL**: capital 1.806.750, interés 2.345, morat. 0, punit. 0, IVA 235, **total
  1.809.330**.

Después de la tabla: **"Efectivo recibido: 1.810.000 Gs. Vuelto: 700 Gs. Aplicado:
1.809.330 Gs. Redondeo: -30 Gs."**; **"Son: UN MILLÓN OCHOCIENTOS NUEVE MIL TRESCIENTOS
TREINTA GUARANÍES."** (el monto **aplicado**, no el recibido ni el efectivo neto — ver **C37**
más abajo); la leyenda **"PRÉSTAMO CANCELADO"**; firmas; **"ORIGINAL: Cliente / COPIA: Capital
Crédito"**; "Emitido por"; "Página 1 de 1".

**Confirma exacto lo ya verificado con `SELECT`** (§ 5): la fila `TOTAL` de esta tabla
(1.806.750 + 2.345 + 0 + 0 + 235 = 1.809.330) es, literalmente, la suma de las 11 filas de
`aplicaciones_pago` de este pago — el recibo no inventa ni recalcula nada, solo imprime lo que
ya quedó guardado cuota por cuota.

### 2) Factura `001-001-0000001`

**Qué factura y qué no**: **solo el interés compensatorio del período en curso** (2.345 Gs.) +
su IVA (235 Gs.) = **2.580 Gs. de total gravado**. No hay moratorio ni punitorio (no había
ninguna cuota vencida — `CC-2026-00002` canceló 29 días antes de su primera cuota). **El
capital (1.806.750 Gs.) queda completamente fuera de la factura** — ni como línea gravada ni
como exenta (RN-25, `Factura.totalExenta` siempre 0, ya documentado en § 4).

**La cuenta exacta del interés de 2 días**, verificada con `SELECT` contra
`liquidaciones_anticipadas` (id 3, `tipo_liquidacion = 'TOTAL'`) y `prestamos.tasa_mensual`:

```
interés devengado = capitalNoVencido × tasaMensual × (díasTranscurridos ÷ díasDelPeríodo)
                   = 1.806.750 × 0,020118 × (2 ÷ 31)
                   ≈ 2.345,05 Gs. → redondeado (HALF_UP) a 2.345 Gs.
IVA del interés    = redondear(2.345 × 0,10) = 234,5 → redondeado (HALF_UP) a 235 Gs.
```

- **Base**: `capitalNoVencido` = la suma del capital pendiente de las 11 cuotas vigentes
  (1.806.750 Gs. — ninguna estaba vencida, así que todo el capital cae en este bucket, no en
  `capitalVencido`).
- **Tasa**: `prestamos.tasa_mensual = 0,020118` (la misma tasa mensual ya usada en los anticipos
  del 23/10, columna real del préstamo, no recalculada a mano).
- **Días**: el período de la cuota 1 va del 23/10/2026 (fecha de desembolso) al 23/11/2026 (31
  días); la cancelación fue el 25/10/2026 → **2 días transcurridos** de ese período, 31 días en
  total — el mismo mecanismo de prorrateo (`periodoActual`/`corresponderProrratearInteresDelPeriodo`)
  ya documentado para los anticipos, aplicado esta vez con días > 0.
- Confirmado exacto con `SELECT`: `liquidaciones_anticipadas.interes_devengado = 2345`,
  `tributos_aplicables = 235`, `monto_total_liquidacion = 1.809.330` (= 1.806.750 + 2.345 + 235).

**Regla del código que decide emitirla**: el **mismo** método ya citado en § 1, sin ninguna
variante para cancelación:

```java
boolean correspondeEmitirFactura(BigDecimal totalGravado) {
    return totalGravado.compareTo(BigDecimal.ZERO) > 0;
}
```

Dentro de `confirmarCancelacionTotal`, `totalGravado` suma el interés/moratorio/punitorio de
las cuotas vencidas (0 Gs. acá, no había ninguna) **más** `liquidacion.getInteresDevengado() +
ivaInteresDevengado()` (2.345 + 235 = 2.580 Gs.) → `2.580 > 0` → **`true`**.

**Por qué los dos anticipos del 23/10/2026 no emitieron factura y este pago sí**: la única
diferencia es `díasTranscurridos`. Los dos anticipos se cobraron el **mismo día** del
desembolso (23/10), con `díasTranscurridos = 0` → interés devengado exactamente **0 Gs.** →
`totalGravado = 0` → `correspondeEmitirFactura` da `false`, sin factura. Esta cancelación
ocurrió **2 días después** del inicio del período → `díasTranscurridos = 2` → el mismo cálculo
da un interés real (2.345 Gs.) → `totalGravado > 0` → factura. **No es una regla distinta para
cancelación** — es la misma fórmula de siempre, con un insumo (los días) que esta vez no fue
cero.

**Dato de negocio para el rediseño del cobro (sin clasificar)**: el interés devengado de un
anticipo o de una cancelación **se prorratea por días reales del período** (`capital ×
tasaMensual × díasTranscurridos ÷ díasDelPeríodo`, acá `2 ÷ 31`) — pero el **plan de cuotas**
que generó ese mismo préstamo usa **mes fijo**, sin mirar los días reales del período
(`ServicioMotorFinanciero.generarPlanPagos`, cada cuota cobra un mes completo de interés;
decisión ya cerrada, D-156/C2, `04_formalizacion.md`). Son dos partes del mismo motor
financiero, sobre el mismo préstamo, que miden el tiempo de forma distinta: una cuota
programada nunca varía con los días reales del mes; un anticipo o una cancelación, sí. No se
decide acá si esto debería unificarse — queda anotado como dato a tener presente si la fase 2
rediseña cómo se calcula el interés en cualquiera de los dos caminos.

#### Lo observado en pantalla (25/10/2026, 11:16): la primera factura real de esta base

"Imprimir factura" abrió la PDF, con:

- Encabezado de la empresa (con "Préstamos personales" y el correo electrónico).
- Recuadro fiscal: **"RUC 80123456-5 — TIMBRADO N.º 16842795 — Vigencia: 01/09/2026 al
  31/08/2027 — FACTURA N.º 001-001-0000001"**.
- "Fecha de emisión: 25/10/2026"; **"Condición de venta: CONTADO ☒ CRÉDITO ☐"**.
- Nombre o razón social, RUC/C.I., Dirección (con **"Barcequillo,,"** — ver **B13**, se
  repite), Operación `CC-2026-00002`, **"Recibo asociado: REC-2026-000003"**.
- Tabla **Cant / Descripción / P. Unit. / Exentas / 5 % / 10 %**: una sola fila, **"1 — Interés
  compensatorio cuota 1/11 — 2.580 Gs."**, en la columna 10 %.
- **SUBTOTALES: 0 / 0 / 2.580**; **"TOTAL A PAGAR: 2.580 Gs."**, en letras; **"LIQUIDACIÓN DEL
  IVA: (5 %) 0 Gs. (10 %) 235 Gs. TOTAL IVA 235 Gs."**; "Tipo de transacción: Prestación de
  servicios"; **"ORIGINAL: Cliente / DUPLICADO: Archivo tributario"**.

**Relación factura (2.580 Gs.) vs. recibo (1.809.330 Gs.)**: son el mismo cobro, mirado con dos
reglas distintas. El recibo (`REC-2026-000003`) acredita **todo** el dinero que se movió —
capital (1.806.750) + interés (2.345) + IVA (235) = 1.809.330 Gs. La factura solo describe la
porción **gravada** de eso — interés + IVA = 2.580 Gs. — porque el capital, por RN-25, nunca se
factura. **2.580 Gs. es un subconjunto de los 1.809.330 Gs. del recibo, no un monto aparte.**

**Verificado en el código**:

- **De dónde salen timbrado, vigencia, establecimiento y punto de expedición**: los cuatro son
  **parámetros** (`parametros_sistema`, clave `fiscal.*`), nunca constantes de código —
  confirmado con `SELECT`: `fiscal.timbrado_numero = 16842795`,
  `fiscal.timbrado_vigencia_desde = 01/09/2026`, `fiscal.timbrado_vigencia_hasta = 31/08/2027`,
  `fiscal.establecimiento = 001`, `fiscal.punto_expedicion = 001` — todos vigentes desde
  21/10/2026 (fecha en que se cargó la semilla de parámetros de esta base de pruebas; son datos
  de prueba, no de producción, pero el **mecanismo** para cambiarlos es un parámetro editable,
  nunca recompilar código). El RUC de la empresa (`80123456-5`) sale de `empresa.ruc`, mismo
  patrón.
- **Cómo se arma "Interés compensatorio cuota 1/11" en una cancelación total**: el PDF se genera
  al primer pedido (D-94) reconstruyendo las líneas desde `aplicaciones_pago`
  (`ServicioCobranza.reconstruirLineasFactura`) — y como `confirmarCancelacionTotal` grabó el
  interés devengado del período en curso como una aplicación `TipoAplicacion.CUOTA` sobre la
  **primera cuota pendiente** (ver el `SELECT` de `06_primer_cobro.md`, cuota con
  `numero_cuota = 1` del plan vigente de 11 cuotas), la reconstrucción cae en el **mismo** método
  que usaría un cobro de cuota vencida normal:

  ```java
  // derivarLineasCuotaFactura, línea ~1631
  String sufijoCuota = " cuota " + numeroCuota + "/" + totalCuotas;
  lineas.add(new LineaFactura("Interés compensatorio" + sufijoCuota, montoInteres, ivaInteres));
  ```

  `numeroCuota = 1`, `totalCuotas = 11` (el tamaño del plan vigente al momento del pago) →
  **"Interés compensatorio cuota 1/11"**. No hay ningún texto propio para "esto es una
  cancelación anticipada, con interés prorrateado por días" — la factura reusa, sin cambiar una
  palabra, el mismo formato que usaría para la cuota 1 de un cobro normal ya vencido (ver
  **B75**).
- **Una o dos copias**: **una sola página**, igual que el recibo — revisado `factura.jrxml`
  completo: no tiene la estructura de dos bandas `title`/`summary` que sí usa
  `comprobante_desembolso.jrxml` para sus "ORIGINAL – Empresa"/"COPIA – Cliente" en páginas
  separadas (B58, ya documentado en § 5). Acá, "ORIGINAL: Cliente / DUPLICADO: Archivo
  tributario" es un único renglón de texto en el `summary`, impreso una sola vez.
- **Control de vigencia del timbrado y qué pasa al vencer**: `validarTimbradoVigente` (dos
  overloads) se llama **antes** de tomar cualquier número, con dos chequeos independientes:
  - **Por fecha**: `fecha.isBefore(timbradoDesde) || fecha.isAfter(timbradoHasta)` → rechaza con
    *"El timbrado no está vigente en la fecha del cobro (vigente del [desde] al [hasta])."*
  - **Por cantidad**: `proximoNumero > numeroHasta` (`fiscal.factura_numero_hasta`) → rechaza
    con *"Timbrado agotado: se alcanzó el último número autorizado (hasta [N]). Solicite un
    nuevo timbrado."*

  En los dos casos, el rechazo ocurre **antes de insertar nada** — y como la validación corre
  dentro de la misma transacción que todo el cobro (D-77 punto 15, ya documentado en § 3), si
  el timbrado está vencido o agotado **se rechaza la cancelación completa**, no solo la
  factura: ni recibo, ni constancia, ni cambio de estado del préstamo — nada queda a mitad de
  camino.
- **Cómo se calcula el IVA — sobre el interés, no como total÷11**: `ServicioMotorFinanciero.
  iva(montoInteres) = redondear(montoInteres × 0,10)` → `redondear(2.345 × 0,10) = 235`. La
  factura **nunca** calcula el IVA dividiendo el total entre 11 — aunque, en este caso puntual,
  **ambas cuentas dan el mismo número** (2.580 ÷ 11 ≈ 234,5 → 235 también), porque
  matemáticamente `total = neto × 1,1` implica `IVA = total ÷ 11` cuando no hay redondeo de
  por medio; la coincidencia es aritmética, no es así como lo calcula el código.

### 3) Constancia de cancelación `CAN-2026-000001`

**Qué acredita**: que el préstamo quedó **sin deuda pendiente**, documento aparte del recibo y
la factura (ninguno de los dos dice "deuda saldada" — solo describen el movimiento de dinero y
lo gravado). El texto fijo de `constancia_cancelacion.jrxml` lo dice explícito: *"Por la
presente, Capital Crédito Paraguay S.R.L. deja constancia de que [cliente]... ha CANCELADO EN
SU TOTALIDAD el préstamo que se detalla, no registrando deuda pendiente por esta operación a
la fecha."* Trae, además, "DATOS DEL PRÉSTAMO" (operación, monto desembolsado, fecha, plazo) y
"LIQUIDACIÓN DE LA CANCELACIÓN" completa (vencidas impagas, capital no vencido, interés del
período, IVA, TOTAL CANCELADO), la referencia a "Comprobantes: Recibo [...] — Factura: [...]",
y la cita de la Ley 6624/2020 (mismo texto que la cláusula SEXTA del contrato).

#### Lo observado en pantalla (25/10/2026, 11:20): la constancia completa

**"CONSTANCIA DE CANCELACIÓN TOTAL DE PRÉSTAMO N.º CAN-2026-000001"**: "Asunción, 25 de
octubre de 2026"; *"Por la presente, Capital Crédito Paraguay S.R.L. deja constancia de que
[cliente], con C.I. N.º [...], ha CANCELADO EN SU TOTALIDAD el préstamo que se detalla, no
registrando deuda pendiente por esta operación a la fecha."*; **"Son:"** 1.809.330 en letras.

**"DATOS DEL PRÉSTAMO"**: operación, fecha de desembolso 23/10/2026, fecha de cancelación
25/10/2026, monto desembolsado 2.000.000, plazo original 12 cuotas mensuales.

**"LIQUIDACIÓN DE LA CANCELACIÓN"**: cuotas vencidas impagas **0**; capital no vencido
**1.806.750**; interés del período en curso **2.345**; IVA sobre intereses **235**; **TOTAL
CANCELADO 1.809.330** — los mismos cuatro números ya verificados con `SELECT` contra
`liquidaciones_anticipadas` en el punto 2.

*"Conforme a la Ley N.º 6624/2020, no se cobraron intereses futuros ni penalidades por
cancelación anticipada."*; **"Comprobantes: Recibo REC-2026-000003 — Factura:
001-001-0000001"**; **"Pagaré: se devuelve al cliente el pagaré original firmado. [   ]"**
(casilla manual); firmas del **representante legal** (nombre, C.I., "Representante legal",
razón social) y **"Recibí conforme (cliente) — Firma y aclaración"**; "Emitido por"; "Página 1
de 1".

**De dónde sale el representante legal**: dos parámetros, `empresa.representante_legal_nombre`
y `empresa.representante_legal_cedula` (confirmados con `SELECT`: "Ramón Benítez" / C.I.
2.845.117) — leídos con `obtenerValorAFechaOMasAntiguo` (mismo criterio histórico que el resto
de los datos de la empresa en comprobantes, para que una constancia vieja siga mostrando el
representante que regía en esa fecha, no el actual).

**Dato (contraste con B73)**: esta constancia **sí** trae el desglose completo de la
liquidación (vencidas impagas, capital no vencido, interés, IVA, total) — a diferencia de la
confirmación previa de "Cancelar todo" (que solo muestra totales, B73) y del recibo (que
desglosa por cuota, no por estos cuatro conceptos). Es el único de los tres documentos que
resume la liquidación en este formato.

**Dato**: la cita de la **Ley N.º 6624/2020** es, literalmente, el mismo texto legal que ya
sustenta la cláusula SEXTA del contrato ("Pago anticipado") — ya rastreado hasta RNF18 del
propio anteproyecto en el hallazgo **C5** (`04_formalizacion.md`). Misma base legal, citada en
dos documentos distintos del mismo préstamo.

**Numeración**: `CAN-2026-000001` — clave `CONSTANCIA`, confirmada con `SELECT`
(`numeradores`: `CONSTANCIA/2026 = 1`, la primera de esta base). `liquidaciones_anticipadas.
numero_constancia = 'CAN-2026-000001'` para la fila `tipo_liquidacion = 'TOTAL'` de este pago.

**Regla del código que la emite solo en cancelación total**: dentro de
`confirmarCancelacionTotal` (línea ~931-933), **sin ninguna condición** — se asigna siempre,
antes incluso de saber si habrá factura:

```java
int anioConstancia = fecha.getYear();
long numeroConstanciaSecuencia = numeradorDAO.siguienteValor(conexion, "CONSTANCIA", anioConstancia);
String numeroConstancia = String.format("CAN-%d-%06d", anioConstancia, numeroConstanciaSecuencia);
```

Este método (`siguienteValor(..., "CONSTANCIA", ...)`) **no aparece en ningún otro camino de
cobro** (`confirmarCobro`/`confirmarAnticipoCapital` nunca lo llaman) — la constancia está
ligada, en el código, exclusivamente a la cancelación total, nunca a si quedó o no algo
gravado.

**Relación con la devolución del pagaré y el cierre documental (RF29) — responde C38**: la
propia constancia trae, impreso, un casillero *"Pagaré: se devuelve al cliente el pagaré
original firmado. [   ]"* y una línea de firma "Representante legal" / "Recibí conforme
(cliente)" — pero ese casillero es **solo texto en el PDF**, sin ningún control real detrás
(no hay ningún parámetro que lo marque, no se guarda si está tildado o no). **El sistema sí
tiene un correlato real, pero en otro lugar, con su propio flujo de 3 pasos**, verificado
leyendo `ServicioFormalizacion` completo:

1. **`PrestamoDAO.marcarFinalizado`** (en la misma transacción de `confirmarCancelacionTotal`)
   cambia `estado_cierre_documental` de `NO_APLICA` a **`PENDIENTE`** — confirmado con `SELECT`:
   `CC-2026-00002` quedó con `estado_prestamo = 'FINALIZADO'` y `estado_cierre_documental =
   'PENDIENTE'`. Ese estado es, exactamente, lo que hace que el préstamo aparezca en la
   bandeja de **Formalización → "Préstamos finalizados — cierre documental pendiente"**
   (`ServicioFormalizacion.listarFinalizadosConCierrePendiente`).
2. **`ServicioFormalizacion.generarConstanciaDevolucion(idPrestamo, idUsuario)`**, desde esa
   bandeja — asigna (idempotente) el número **`DEV-aaaa-nnnnnn`** y genera el PDF de la
   "Constancia de devolución" — un documento **distinto** de la constancia de cancelación, que
   el cliente firma al recibir físicamente su pagaré de vuelta. Confirmado con `SELECT`: hoy
   `numeradores` no tiene ninguna fila `DEVOLUCION` y `prestamos.numero_constancia_devolucion`
   está vacío — este paso **todavía no se ejecutó**.
3. **`ServicioFormalizacion.completarCierreDocumental(idPrestamo, idUsuario)`** — exige que el
   escaneo **firmado** de esa constancia ya esté cargado como documento (tipo
   `DEVOLUCION_PAGARE_FIRMADO`, mismo mecanismo de carga que el resto del checklist de
   Formalización); si no, rechaza con *"Cargue el escaneo firmado de la constancia de
   devolución antes de completar el cierre documental."* Solo entonces marca
   `estado_cierre_documental = 'COMPLETO'` y audita `COMPLETAR_CIERRE_DOCUMENTAL`.

**Quién lo hace**: la misma ruta y permiso que el resto de Formalización
(`Permiso.GENERAR_DOCUMENTOS_FORMALIZACION`, `TOTAL` para oficial y gerente por diseño, D-68) —
ningún perfil nuevo, ninguna pantalla fuera de `/formalizacion`. **Conclusión de C38**: el
sistema **sí** registra la devolución del pagaré, con su propio documento y su propio estado
(`estado_cierre_documental`), pero **nunca a través de la casilla impresa en la constancia de
cancelación** — esa casilla es puramente informativa para quien firma en papel. Verificado en
el código; **el recorrido en pantalla de estos 3 pasos queda para el próximo paso** (ver
`00_LEEME.md` § "PUNTO DE RETOMADA").

### 3.5) Antes de confirmar: la ficha y la confirmación de "Cancelar todo" (25/10/2026, 10:54–10:55)

Desarrollo completo en `06_primer_cobro.md` § Paso 16 — acá solo el resumen relevante para los
comprobantes. La ficha mostró **"Cancelar todo 1.809.330 — en efectivo 1.809.300"** (ya con los
2 días de interés + IVA sumados sobre los 1.806.750 Gs. del 23/10). Con "Cancelar todo"
seleccionado, el selector "1/2/3 cuotas" y "Si adelanta…" de la tarjeta de adelanto **siguieron
visibles**, sin ningún efecto real sobre esta operación (**B74**). La confirmación mostró el
total, lo recibido y el vuelto, **sin desglose** (ver B73 arriba), con los botones **"Sí,
cancelar todo"**/**"Cancelar"** (**B3, peor caso de esta familia** — un solo adjetivo de
diferencia entre confirmar y cancelar la operación). Tras confirmar, el préstamo pasó a
**"Finalizado"** y una nueva fila del historial, **"Cancelación total"**, siguió ofreciendo
**"Solicitar reversión"** con la misma casilla de la factura que cualquier otro cobro — ver
**C35** más abajo.

### 4) Tabla resumen: operación → documentos que emite y por qué

| Operación | Recibo | Factura | Constancia | Por qué |
|---|---|---|---|---|
| **Adelanto a capital, interés 0** | Sí (siempre) | **No** | No | `correspondeEmitirFactura(0) = false`; no cambia el estado del préstamo |
| **Adelanto a capital, interés > 0** | Sí | **Sí** | No | `totalGravado > 0` (interés del período en curso); el préstamo sigue `ACTIVO`, no hay constancia |
| **Pago de más / vuelto** | Sí (con "Vuelto") | Según haya o no gravado en lo aplicado | No | El vuelto nunca es un concepto facturable; no cancela ni finaliza nada |
| **Cobro de una cuota vencida** | Sí | Sí si interés/moratorio/punitorio de esa cuota > 0 | No | Cobro normal — la constancia solo nace de `confirmarCancelacionTotal`, nunca de `confirmarCobro` |
| **Cancelación total** | Sí | Sí si queda interés devengado del período en curso > 0 (**este caso real**: 2.345+235=2.580>0) | **Sí, siempre** (`CAN-aaaa-nnnnnn`) | La constancia está ligada al cambio de estado a `FINALIZADO`, no al monto gravado — se asigna sin condición en `confirmarCancelacionTotal` |
| **Reversión, autorizada** | El original se **regenera** anulado (mismo número) | Se anula + **Nota de anulación** si había factura | **Constancia de reversión** (`REV-aaaa-NNNNNN`), siempre | Evento distinto de un cobro — auditado aparte (`AUTORIZAR_REVERSION`), nunca reutiliza la lógica de `correspondeEmitirFactura` |
| **Desembolso** | No (no hay `Pago`) | No | No | No es un cobro — comprobante propio, `DES-aaaa-NNNNNN`, otra entidad (`Desembolso`) |

### 5) Los montos cuadran entre sí — verificado con `SELECT`

```
pagos (id 3, REC-2026-000003): monto_recibido=1.810.000  monto_vuelto=700
                                monto_aplicado=1.809.330  monto_redondeo=30

recibido − vuelto         = 1.810.000 − 700       = 1.809.300   (lo que de verdad quedó en caja)
cobrado + redondeo perdonado = 1.809.300 + 30      = 1.809.330   = monto_aplicado ✓
total aplicado = capital + interés + IVA = 1.806.750 + 2.345 + 235 = 1.809.330 ✓

movimientos_caja (sesión N.º 5): COBRO +1.810.000, VUELTO −700 → efecto neto +1.809.300 ✓
aplicaciones_pago (11 filas, id_pago=3): Σ monto_capital = 1.806.750; Σ monto_interes = 2.345
                                          (solo en la cuota 25); Σ monto_tributos = 235
                                          Σ subtotal_aplicado = 1.809.330 ✓
factura (id 1): total_gravada_10 = 2.580 = interés (2.345) + IVA (235) — sin el capital ✓
```

Las cuatro fuentes (recibo/`pagos`, caja, `aplicaciones_pago` y la factura) describen el mismo
dinero desde cuatro ángulos distintos — y los cuatro cuadran exacto, sin ningún resto suelto ni
redondeo perdido.

### Hallazgos de este caso

- **B73 (matizar)** — El desglose cuota por cuota (capital/interés/moratorio/punitorio/IVA de
  cada una de las 11 cuotas) **existe, pero solo aparece en el recibo, después de confirmar el
  cobro** — verificado en `cobranza-ficha.js`: la confirmación de "Cancelar todo" (título
  "Confirmar: Cancelar todo") solo muestra tres líneas, **todas en total**: *"Cancelar todo:
  [monto exacto]"*, *"Recibido: [monto]"*, *"Sin vuelto (0 Gs.)"* o *"Vuelto: [monto]"* — sin
  ningún desglose por cuota. La cajera (o el cliente, si mira la pantalla) recién ve de dónde
  sale cada guaraní **después** de confirmar, cuando ya es irreversible. No es que falte el
  dato en el sistema — ya se calcula cuota por cuota antes de confirmar
  (`ServicioMotorFinanciero.calcularCancelacionTotal`) — es que la pantalla de confirmación no
  lo muestra, y el recibo sí.
- **B75** — En una cancelación total, la factura describe el interés devengado como **"Interés
  compensatorio cuota 1/11"** — el mismo formato que usaría para el interés de una cuota
  vencida real, sin ningún texto que indique que se trata de una **cancelación anticipada**
  (Ley 6624/2020, cláusula SEXTA) ni que ese monto es un prorrateo por días (2 de 31, en este
  caso). Sugerencia: una descripción propia para este camino, del tipo "Interés compensatorio
  por cancelación anticipada — [N] días del período en curso", para no confundir este concepto
  con el interés normal de una cuota vencida.
- **B13 (se repite)** — La dirección del cliente, con **"Barcequillo,,"** (coma duplicada),
  aparece también en la factura — el mismo defecto ya documentado en el contrato
  (`04_formalizacion.md`) y en el comprobante de desembolso (`05_desembolso.md`), ahora
  confirmado en un **documento fiscal**. Verificado: `domicilioSinReferencia` (línea 1039 de
  `ServicioClientes.java`) solo recorta un paréntesis final (la referencia coloquial, "casa
  blanca con portón verde") con una expresión regular — **nunca toca una coma duplicada dentro
  de la dirección misma** (p. ej. cuando el campo de barrio queda vacío al cargar el cliente).
  Son dos problemas distintos del mismo campo; la corrección que ya existe soluciona uno, no el
  otro.
- **C36 (para el contador/revisión legal, sin opinar sobre normativa)** — Pendiente verificar
  que el formato de esta factura (autoimpresor, sin SIFEN) cumpla la normativa vigente de la
  SET, y si el timbrado configurado (`16842795`, vigente 01/09/2026-31/08/2027) es un dato de
  prueba o uno real asignado a Capital Crédito. Relacionado con **RF23**
  (`RF23_recibo_factura.md` § 10, ya pendiente de revisión legal) y **RF33**
  (`RF33_facturas_iva.md`, el informe mensual que se apoya en estas mismas facturas). No se
  verifica ni se opina sobre la normativa en este documento — solo se registra que la revisión
  sigue pendiente, ahora con un ejemplo real y no solo hipotético.
- **C37 (para el contador, sin opinar sobre normativa)** — En una cancelación con redondeo
  perdonado, el **"Son:"** del recibo expresa el monto **aplicado** a la deuda (1.809.330 Gs.),
  **no** el efectivo neto que de verdad entró a la caja (recibido − vuelto = 1.810.000 − 700 =
  1.809.300 Gs. — 30 Gs. menos). Verificado: `recibo.jrxml` usa `APLICADO_LETRAS`, siempre el
  monto aplicado, el mismo criterio para cualquier cobro, no una excepción de este caso.
  **Queda por verificar con el contador** cuál es el criterio correcto para un documento que
  acredita un pago: ¿el monto que efectivamente cambió de manos (1.809.300), o el monto que
  quedó saldado en la deuda (1.809.330, 30 Gs. más, que la empresa perdonó)? **Relacionado con
  C25** (`06_primer_cobro.md`): en un adelanto a capital **no existe** este mecanismo de
  redondeo perdonado — `confirmarAnticipoCapital` recibe `montoAplicado` como parámetro directo,
  sin ninguna lógica de redondeo interna, así que ese "Son:" nunca se aparta de lo realmente
  entregado. La pregunta de C37 es exclusiva de los caminos que sí perdonan el redondeo (cobro
  normal y cancelación total).
- **B74** — Con **"Cancelar todo"** ya seleccionado, el selector **"1/2/3 cuotas"** y el texto
  **"Si adelanta…"** de la tarjeta de "Adelantar a capital" siguen visibles y habilitados, sin
  ningún sentido para esta operación. Desarrollo en `06_primer_cobro.md` § Paso 16.
- **B3 (peor caso de esta familia)** — La confirmación de "Cancelar todo" ofrece **"Sí, cancelar
  todo"** junto a **"Cancelar"** — la variante más parecida entre sí de todas las ya registradas
  de este hallazgo (`02_solicitud.md`, `03_aprobacion.md`).
- **C35 — Verificado en el código, sin probarlo.** Un préstamo `FINALIZADO` con factura sigue
  ofreciendo **"Solicitar reversión"**, con la misma casilla de la factura, mientras el cierre
  documental no esté `COMPLETO` — **por diseño, no es un hueco**:
  `ServicioReversiones.validarPuedeSolicitarReversion`/`autorizarReversion` revalidan en los dos
  momentos (solicitar y autorizar) y, una vez `COMPLETO`, la ficha deja de ofrecer el formulario
  y muestra **"No admite reversión: pagaré devuelto al cliente"**. Si se autorizara (con la
  factura devuelta), el sistema restaura las 11 cuotas, saca al préstamo de `FINALIZADO`, anula
  la factura y emite una **Nota de anulación** (`NAN-aaaa-nnnnnn`) — no una "Nota de Crédito"
  literal, a pesar de que el texto en pantalla (la casilla de la cajera) hable de "nota de
  crédito"; sin la factura devuelta, el sistema rechaza directo y dice que correspondería una
  Nota de Crédito "que no está disponible en esta versión" (limitación ya conocida desde antes
  de este recorrido, DNIT Decreto 6539/05 vs. RG 118/2017, a confirmar con el contador cuál
  norma aplica). **Implicancia fiscal para el contador**: confirmar si "Nota de anulación" es el
  tratamiento correcto, en vez de la "Nota de Crédito" que menciona la pantalla. Desarrollo
  completo en `06_primer_cobro.md` § Paso 16.
- **C38 — Cubierto, confirmado en pantalla el 25/10/2026.** La devolución del pagaré figura en
  la constancia de cancelación como una **casilla a marcar a mano**, sin ningún control real
  detrás de ese PDF — pero **sí existe** un correlato real del sistema, en un flujo de 3 pasos
  aparte (`marcarFinalizado` → `generarConstanciaDevolucion` → `completarCierreDocumental`, con
  su propia Constancia de devolución `DEV-2026-000001` y su propio documento firmado cargado),
  desarrollado por completo arriba y ahora **recorrido en pantalla de punta a punta** (primera
  vez en todo el proyecto) — ver `04_formalizacion.md` § "Tercera pasada de formalización:
  cierre documental".

## 8. RF y documentos relacionados

- **RF22** (`RF22_cobro_efectivo.md`): dispara `confirmarCobro`, de donde nacen recibo y
  factura del cobro normal/parcial — este documento no tiene disparador propio.
- **RF23** (`RF23_recibo_factura.md`, probado en pantalla 12/10/2026, tag `rf23-probado`):
  el RF específico de recibo/factura — anteproyecto: *"El sistema debe emitir el recibo de cada
  cobro y la factura de los intereses con su IVA."* Fuente principal de § 1, § 3 y § 4 de este
  documento; también deja anotado que **la factura está pendiente de revisión de un contador**
  antes de usarse con clientes reales (RF23 § 10), y documenta el error-y-corrección sobre si
  `totalGravado` incluye o no el IVA (sí lo incluye, D-85, `calcularTotalesFactura`).
- **RF27** (`RF27_adelantar_capital.md`): el camino de adelanto a capital, con y sin interés
  devengado — fuente de la prueba real de Mirtha citada en § 7.
- **RF28** (`RF28_constancia_cancelacion.md`, tag `rf28-probado`): constancia de cancelación,
  `CAN-aaaa-nnnnnn`, misma transacción que `confirmarCancelacionTotal`.
- **RF25** (`RF25_reversion_cobro.md`): reversión de un cobro — anula la factura (nunca
  `DELETE`), regenera el recibo con la franja roja, emite la nota de anulación y la constancia de
  reversión.
- **RF33** (`RF33_facturas_iva.md`, tag `rf33-probado`): informe de IVA del mes y de facturas
  anuladas — confirmado que una factura `ANULADA` nunca suma al IVA del mes (`continue` explícito
  en `calcularTotalesIva`), pero sigue apareciendo en el listado (marcada) para no dejar un hueco
  sin explicar en la numeración correlativa (RN-25).
- **RF17** (`RF17_comprobante_desembolso.md`, tag `rf17-probado`): el comprobante de
  desembolso — no es parte de este flujo de cobro, pero es la referencia de contraste de § 5/§ 6
  (2 páginas, marca de "REIMPRESIÓN", auditoría propia de reimpresión).
- **RN-25/RN-26** (`docs/00_especificacion_maestra.md`, citadas también en RF23/RF33): numeración
  correlativa sin saltos, solo conceptos gravados en la factura, conservación 5 años sin borrado.
- **RF34** (`RF34_estado_cuenta.md`): el Informe de estado de cuenta, única vía para entregar el
  plan completo en papel (cerrado en `06_primer_cobro.md` § Paso 4, hallazgo C26).

## 9. Observaciones para la fase 2

Sin clasificar A/B, sin proponer código — para que Joel o el dueño de Capital Crédito decidan.

1. **Criterio de original/copia, sin unificar** (desarrollo en § 5/hallazgo B58): tres palabras
   distintas para lo mismo entre recibo/factura/desembolso, y cuatro comprobantes que no dicen
   nada al respecto.
2. **Auditoría de reimpresión, inconsistente entre comprobantes** (§ 6): el comprobante de
   desembolso audita cada reimpresión (`REIMPRIMIR_COMPROBANTE_DESEMBOLSO`); el recibo, la
   factura, las tres constancias y la nota de anulación, no auditan ninguna reimpresión.
3. **`TASA_IVA` (10 %) hardcodeada en Java**, a diferencia de todos los demás datos fiscales
   (timbrado, establecimiento, punto de expedición, rangos de numeración), que sí son parámetros
   editables sin tocar código — una suba o baja futura del IVA exigiría modificar y recompilar
   `ServicioMotorFinanciero`, no cambiar un parámetro.
4. **Columna "Gravada 5 %" en `factura.jrxml` sin ningún uso real** — el negocio nunca genera
   conceptos al 5 %, el campo siempre imprime 0; no es un error, pero es una plantilla con un
   campo muerto.
5. **Revisión legal de la factura, todavía pendiente** (ya anotado en `RF23_recibo_factura.md`
   § 10 y en `AVANCE.md`, "Antes de entregar"): falta la firma y aclaración del emisor en
   `factura.jrxml`, y en general la confirmación de un contador/abogado de que el formato cumple
   la normativa de autoimpresor vigente. No se verifica la normativa en este documento — solo se
   registra que sigue abierto.
6. **El "Resumen del plan regenerado" solo existe en el recibo de un anticipo** — un cobro
   normal o una cancelación total no llevan un resumen equivalente del estado del plan después
   del pago (la cancelación sí dice "PRÉSTAMO CANCELADO", que es más simple porque no queda plan
   vigente); no se encontró incoherencia, solo una asimetría de diseño entre variantes del mismo
   reporte.
7. **La relación pago-recibo-factura depende enteramente de recalcular desde `aplicaciones_pago`
   al reimprimir** (D-93: ninguno de los dos guarda sus propias líneas) — funciona hoy porque
   `aplicaciones_pago` nunca se borra ni se modifica, pero es una dependencia implícita que vale
   la pena tener presente si la fase 2 cambia cómo se guardan las aplicaciones de un pago.
