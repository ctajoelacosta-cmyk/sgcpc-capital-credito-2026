# RF23 — Recibo de cada cobro y factura de los intereses con IVA

**Estado: probado en pantalla el 12/10/2026 (tag `rf23-probado`).** Las correcciones de
presentación del recibo y la factura (ver §9) ya se probaron en pantalla y quedaron OK. **La
factura queda pendiente de revisión del contador** (parte legal y delicada — ver §10, el
contenido obligatorio según la normativa de la DNIT todavía no está confirmado contra la fuente
oficial) — no se toca nada más de la factura sin esa revisión y sin la aprobación explícita de
Joel.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF23 (módulo Cobranza y mora): *"El sistema debe emitir el recibo de
cada cobro y la factura de los intereses con su IVA."*

Reglas relacionadas (`docs/00_especificacion_maestra.md`):
- **RN-25**: "Toda factura muestra timbrado, vigencia, RUC y numeración 001-001-NNNNNNN
  correlativa sin saltos; incluye solo intereses, moratorio y punitorio gravados 10 % con IVA
  incluido; el capital se documenta en el recibo, no en la factura (modificada por decisión de
  Joel el 26/09/2026, según práctica de financieras en Paraguay)."
- **RN-26**: conservación mínima 5 años, sin borrado — aplica a recibos y facturas igual que al
  resto.
- Doc. 00, línea 126: *"El sistema emite factura con timbrado; la integración con el sistema de
  facturación electrónica SIFEN (DNIT/e-Kuatia: KuDE, CDC, QR, XML firmado) queda fuera del
  alcance y se propone como trabajo futuro."* — ver §6d.
- RF-41 (numeración anterior del mismo documento, previa a la actual RF01-RF40): "Factura
  (autoimpresor): numeración correlativa sin saltos; datos de timbrado vigentes; capital exento,
  intereses y mora gravados 10 %; reimpresión como copia; anulación solo por reversión."

## 2. Cómo funciona

Ambos documentos se emiten dentro de la misma transacción de `confirmarCobro` (RF22) — nunca por
separado:

1. **Recibo**: se emite SIEMPRE, cobro normal o parcial, haya o no algo gravado. Número
   `REC-AAAA-NNNNNN` (correlativo por año calendario, `NumeradorDAO` clave `RECIBO`).
2. **Factura**: se emite SOLO si lo cobrado incluye algo gravado (interés, moratorio o punitorio
   > 0) — `ServicioCobranza.correspondeEmitirFactura(totalGravado)`. Un cobro 100 % capital (al
   final de un préstamo, por ejemplo) genera recibo pero no factura.
2b. **El "Son:" del recibo deletrea el EFECTIVO RECIBIDO**, no el monto aplicado (C37,
   01/11/2026, decisión de Joel). Con el redondeo de D-141 los dos no son el mismo número: si el
   cliente entrega 844.000 por una deuda de 844.004, el recibo dice *"Son: OCHOCIENTOS CUARENTA Y
   CUATRO MIL GUARANÍES"* y muestra aparte *"Aplicado: 844.004 Gs. · Redondeo: -4 Gs."*. Un
   recibo de dinero certifica **lo que entró a la caja**; lo que se hizo con esa plata ya está en
   sus propios renglones. Parámetro `RECIBIDO_LETRAS` del `recibo.jrxml`, en los dos ejemplares.
   **La constancia de cancelación (RF28) es al revés y está bien así**: deletrea el total
   cancelado (844.004), porque certifica **la deuda saldada**, no el efectivo (D-142).
2c. **El concepto de cada línea de la factura tiene un solo dueño**: `ServicioCobranza.ConceptoGravado`
   ("Interés compensatorio", "Interés moratorio", "Interés punitorio") más
   `conceptoFacturaDeCuota` (*"Interés compensatorio cuota 1/6"*) y `conceptoFacturaDeAnticipo`
   (*"Interés compensatorio hasta el 30/10/2026 (anticipo de capital)"*) — B102, 01/11/2026.
   Antes el texto estaba escrito en **tres** armadores distintos y el de emisión ya decía algo
   diferente del que se imprime. Como la factura **no guarda sus renglones** y se reconstruyen de
   `aplicaciones_pago` al imprimir (D-93/D-94), el texto elegido es **el que ya se imprimía**:
   cambiarlo habría cambiado facturas ya emitidas al reimprimirlas. Por esa misma razón se
   **descartó B75** (describir la cancelación anticipada en vez de "cuota N/M").
3. Antes de tomar cualquier número de factura, se valida que el timbrado esté vigente en la
   fecha del cobro y que no esté agotado (ver §6d) — si falla, **todo el cobro se cancela**, no
   solo la factura (la validación ocurre antes de insertar nada).
4. El PDF de cada uno se genera la primera vez y se **guarda** en PostgreSQL
   (`archivos_binarios`); toda reimpresión posterior devuelve el mismo PDF ya guardado, nunca
   uno regenerado de nuevo (`ServicioCobranza.obtenerOGenerarPdf`, con bloqueo para que dos
   pedidos simultáneos no generen dos PDF distintos del mismo comprobante).

### 2.1 Cómo se ve la factura (rediseño del 27/10/2026)

La factura es un **formulario de talonario**, no una hoja impresa: autoimpresor con timbrado,
**no** factura electrónica (no lleva ni puede llevar KuDE, CDC ni QR). De arriba hacia abajo:

1. **Recuadro del emisor** (borde, esquinas redondeadas): logo a la izquierda; al centro razón
   social, **actividad económica**, domicilio, teléfono, WhatsApp y correo; a la derecha un
   sub-recuadro con RUC, timbrado, vigencia, **FACTURA** en grande y el número
   `001-001-NNNNNNN` en negrita.
2. **Recuadro del cliente y la operación**: fecha de emisión, nombre o razón social, RUC/C.I. y
   dirección a la izquierda; condición de venta (CONTADO ☒ / CRÉDITO ☐), moneda, tipo de
   transacción, operación y recibo asociado a la derecha.
3. **Tabla de detalle** con todas las líneas negras y encabezado gris de dos niveles:
   Cant. | Descripción | Precio unitario | *Valor de venta* (Exentas | 5 % | 10 %).
4. **Totales dentro de la tabla**, cada uno en su fila enmarcada: subtotales por columna, total
   de la operación (en número y en letras) y liquidación del IVA.
5. **Ejemplares** al pie de la tabla, en dos celdas.

Dos detalles de maquetación que conviene conocer antes de tocar el `.jrxml` (están comentados
ahí mismo):

- **El alto de la tabla es fijo**: las verticales de las columnas bajan hasta el pie aunque haya
  un solo renglón. Se consigue dibujando la grilla en el `<background>` a coordenadas fijas
  (y=206 a y=666) y pinando los totales al pie con `isFloatColumnFooter="false"`. Las x de las
  columnas están repetidas en cuatro lugares (background, `columnHeader`, `detail`,
  `columnFooter`): si se mueve una columna, hay que moverla en los cuatro.
- **La franja de ANULADA tiene alto fijo dentro del `<title>`** (y=161..178) en vez de ser un
  `pageHeader` propio. Si fuera una banda aparte, correría todo 46 px hacia abajo y la grilla
  del background quedaría desalineada solo en las facturas anuladas. El sello diagonal del
  background (RF25, punto 7) no cambió.

## 3. Auditoría

No hay un evento de auditoría propio para "emitir recibo"/"emitir factura" — quedan implícitos
dentro del evento `COBRAR` de RF22 (un solo pago, un solo evento de auditoría, con ambos
documentos ya generados antes del `commit`).

## 4. Dónde está

- **Servicio**: `ServicioCobranza.emitirFactura` (crea la fila `facturas`, valida que las líneas
  cuadren exacto con los totales ya calculados — `validarLineasFactura`); `.generarPdfRecibo`/
  `.generarPdfFactura` (arman los parámetros y llaman a JasperReports); `.reconstruirLineasFactura`
  (para reimprimir, deriva las líneas desde `aplicaciones_pago`, nunca las guarda aparte).
- **Reportes**: `recibo.jrxml`/`recibo.jasper`, `factura.jrxml`/`factura.jasper` (ambos en
  `src/main/resources/reportes/`, JasperReports, igual que todo el sistema).
- **DAO**: `FacturaDAO` (tabla `facturas`, incluida `marcarAnulada`); `NumeradorDAO` (claves
  `RECIBO` y `FACTURA`, cada una con bloqueo `FOR UPDATE` antes de incrementar — ver §6d).
- **Modelo**: `Factura` (timbrado, establecimiento, punto de expedición, número, fecha, cliente,
  totales exenta/gravada10/iva10, estado `EMITIDA`/`ANULADA`); `getNumeroCompleto()` =
  `establecimiento + "-" + puntoExpedicion + "-" + numero` (el "001-001-0000001" de RN-25).
- **Parámetros fiscales** (`parametros_sistema`, nunca hardcodeados): `fiscal.timbrado_numero`,
  `fiscal.timbrado_vigencia_desde`/`_hasta`, `fiscal.establecimiento`, `fiscal.punto_expedicion`,
  `fiscal.factura_numero_desde`/`_hasta`.

## 5. Dependencias

- **RF22**: ambos documentos se generan dentro de `confirmarCobro` — este RF23 no tiene un
  disparador propio.
- **RF21**: los montos de interés/moratorio/punitorio que factura ya vienen calculados por RF21.
- **RF25**: la reversión de un cobro anula la factura asociada (`marcarAnulada`, nunca `DELETE`)
  y genera una nota de anulación — ver §6d.
- **RF33** (facturas del mes, IVA): el informe de facturas emitidas/anuladas se apoya en el
  mismo estado `EMITIDA`/`ANULADA` de este RF23 — no analizado todavía en profundidad (queda
  para cuando toque RF33).

## 6. Análisis (13/10/2026, pedido de Joel)

**c) RECIBO — qué lleva, numeración, reimpresión**:
- **Numeración**: `REC-AAAA-NNNNNN`, correlativa **por año calendario** (a diferencia de la
  factura — ver (d)), vía `NumeradorDAO.siguienteValor(conexion, "RECIBO", año)`.
- **Contenido** (según el PDF, `recibo.jrxml` — no abrí el detalle completo del diseño en esta
  pasada, lo reviso si Joel lo pide): identifica el pago, el préstamo, el cliente, y el detalle
  de lo cobrado (capital + interés + moratorio + punitorio + IVA de cada uno, por cuota) —
  incluye el **capital**, a diferencia de la factura (RN-25).
- **Reimpresión**: el PDF se genera una sola vez y se guarda (`archivos_binarios`); reimprimir
  devuelve exactamente el mismo archivo, nunca uno recalculado de nuevo — no hay watermark de
  "copia"/"reimpresión" en el recibo ni en la factura (a diferencia del comprobante de
  desembolso, RF17, que sí distingue la primera impresión).

**d) FACTURA — MUY IMPORTANTE, parte legal**:

- **¿Autoimpresor o electrónica (SIFEN)?** **Autoimpresor con timbrado**, explícito en la
  especificación: `fiscal.tipo_comprobante = AUTOIMPRESOR` (Doc. 00 §5.3) y, en el mismo
  documento (línea 126): *"la integración con el sistema de facturación electrónica SIFEN
  (DNIT/e-Kuatia: KuDE, CDC, QR, XML firmado) queda fuera del alcance y se propone como trabajo
  futuro."* — también anotado como riesgo R-03 ("Obligación de SIFEN a corto plazo") con la
  mitigación "modelo de factura aislado; SIFEN en V2". Esto **no es una omisión del sistema**,
  es una decisión ya documentada y con el riesgo ya identificado — correcto reportarlo así en la
  defensa, no como algo pendiente de corregir.
- **Timbrado y su vigencia**: todo por parámetro — `fiscal.timbrado_numero`,
  `fiscal.timbrado_vigencia_desde`/`_hasta`. `ServicioCobranza.validarTimbradoVigente` rechaza
  CUALQUIER cobro con conceptos gravados si la fecha del cobro no está entre esas dos fechas —
  el PDF de la factura también muestra el número y la vigencia (`factura.jrxml`, parámetros
  `TIMBRADO_NUMERO`/`TIMBRADO_DESDE`/`TIMBRADO_HASTA`).
- **"Datos de la imprenta": no corresponden** (01/11/2026). En un comprobante **autoimpresor**
  los datos de la imprenta (nombre y RUC del establecimiento gráfico, número de habilitación) no
  van: ese bloque es propio de los comprobantes **preimpresos por una imprenta habilitada**. Acá
  el emisor imprime sus propios formularios con su timbrado, así que el dato que identifica la
  autorización es el **timbrado y su vigencia**, ya presentes en el `.jrxml`
  (`TIMBRADO_NUMERO`/`TIMBRADO_DESDE`/`TIMBRADO_HASTA`). Queda escrito porque es un faltante que
  cualquiera puede "encontrar" comparando contra una factura preimpresa: **no es una omisión**.
- **Numeración 001-001-0000001 correlativa sin saltos — cómo se garantiza**: `NumeradorDAO.
  siguienteValor` hace `SELECT ... FOR UPDATE` sobre la fila del numerador antes de incrementarla
  — bloqueo real de base de datos, así que dos cobros simultáneos nunca pueden tomar el mismo
  número ni dejar un hueco. La clave NO es el año calendario del cobro (a diferencia del
  recibo): es el año en que **empezó la vigencia del timbrado actual**
  (`fiscal.timbrado_vigencia_desde`) — la numeración sigue correlativa mientras el timbrado no
  cambie, y solo se reinicia en 0 cuando se carga un timbrado nuevo (una `vigencia_desde`
  distinta), que es exactamente cuándo corresponde reiniciar. Antes de tomar el número también
  se valida "timbrado agotado" (`fiscal.factura_numero_hasta`) — si el próximo número superaría
  el límite autorizado, el cobro se rechaza con un mensaje claro, sin inventar un número.
- **RUC/C.I. del cliente**: sí — `factura.jrxml` imprime `"RUC / C.I.: " + CLIENTE_DOCUMENTO`,
  además de nombre y dirección del cliente, y el RUC de la empresa.
- **IVA 10 % solo sobre intereses (y moratorio/punitorio)**: confirmado — `totalGravado` en
  `confirmarCobro` suma únicamente `interesConIva + moratorioConIva + punitorioConIva` de cada
  cuota (RF21/RF22); nunca incluye capital.
- **El capital no se factura**: confirmado, `Factura.totalExenta` queda siempre en `0` (no hay
  ningún concepto exento en este negocio — RN-25 dice que el capital "se documenta en el
  recibo, no en la factura", así que ni siquiera aparece como línea exenta) y `totalGravada10`
  es exactamente `totalGravado` (solo interés+moratorio+punitorio). El capital cobrado solo
  queda en el recibo y en `aplicaciones_pago.monto_capital`.
- **Anulación sin borrar (RF33)**: confirmado — `FacturaDAO.marcarAnulada` (llamado desde
  `ServicioReversiones`, nunca directo) cambia `estado` a `ANULADA` y guarda
  `id_reversion_pago`/`numero_nota_anulacion` — **nunca un `DELETE`** (coherente con RN-12/RN-26
  y con el resto del sistema). Se emite además una "nota de anulación" (PDF propio,
  `obtenerPdfNotaAnulacion`), documento legal de respaldo de la anulación.
- **Datos legales presentes**: razón social y RUC de la empresa, timbrado + vigencia, nombre y
  RUC/C.I. del cliente, fecha de emisión, número de factura completo (establecimiento-punto-
  número), condición de venta (contado), descripción y monto de cada concepto, subtotales
  exenta/gravada 10 %, total a pagar en letras, liquidación del IVA, tipo de transacción
  ("Prestación de servicios"), leyenda "ORIGINAL: Cliente / DUPLICADO: Archivo tributario".
- **Qué podría faltar — sin confirmar en esta pasada**: no revisé si el PDF declara
  explícitamente que es un comprobante AUTOIMPRESOR (vs. uno "legal" genérico) ni si hace falta
  alguna leyenda específica de la normativa paraguaya de autoimpresores que no haya buscado en
  este análisis — recomiendo que esto lo revise un contador o abogado antes de usarse con
  clientes reales (ya está anotado así en `AVANCE.md`, sección "Antes de entregar").

## 7. Decisiones

**27/10/2026 — Rediseño de la factura como formulario de talonario** (pedido del docente y de
Joel; análisis de solo lectura primero, aprobación de Joel después, ninguna decisión tomada por
cuenta propia). Solo presentación: los mismos datos, los mismos cálculos y los mismos
parámetros; no se tocó `emitirFactura`, `calcularTotalesFactura` ni `reconstruirLineasFactura`.
Ver §2.1 para el diseño y los dos detalles de maquetación. Las cinco decisiones de Joel:

1. **Actividad económica desde `empresa.actividad`** (opción B de las dos propuestas). Es un
   dato **preimpreso obligatorio** (Decreto 6539/05, art. 19) y el parámetro ya existía cargado
   ("Otorgante de Crédito Dinerario (OCD) / Casa de Crédito"), pero **no lo usaba nadie**: el
   `.jrxml` imprimía el texto fijo "Préstamos personales", escrito a mano. Ahora llega como
   parámetro `EMPRESA_ACTIVIDAD`. La línea de Java va **dentro de `generarPdfFactura`**, no en
   `parametrosEmpresaAFecha`: ese método lo comparten cinco documentos (recibo, factura,
   constancia de cancelación, nota de anulación, constancia de reversión) y ninguno de los
   otros cuatro lleva actividad económica. Misma fecha de referencia que el resto de los datos
   de empresa: la del documento, nunca la de hoy (D-94).
2. **"Total de la operación" lee `$P{TOTAL}`**, no `$P{TOTAL_GRAVADA_10}` como antes. Hoy dan el
   mismo número (exentas y 5 % son siempre 0, RN-25), pero `TOTAL` es la suma de las tres
   columnas y es el que concuerda con `TOTAL_LETRAS`, que se arma desde `factura.getTotal()`.
3. **"TOTAL IVA" queda como estaba**, mostrando `IVA_10`. Correcto mientras `IVA_5` sea siempre
   0; sumarlos de verdad sería cálculo, y en este rediseño no se toca ningún cálculo.
4. **Leyenda de ejemplares: dos celdas enmarcadas al pie de la tabla**, "ORIGINAL — CLIENTE" y
   "COPIA — ARCHIVO TRIBUTARIO" (Decreto 6539/05: los ejemplares son ORIGINAL para el
   adquirente y COPIA para el archivo tributario del contribuyente). Antes decía "ORIGINAL:
   Cliente / DUPLICADO: Archivo tributario". **Solo en la factura**: B58 de `FASE2_PLAN.md`
   (igualar las palabras con `recibo.jrxml` y `comprobante_desembolso.jrxml`) sigue abierto, y
   la diferencia entre los tres documentos es ahora mayor, no menor.
5. **Franja de ANULADA de alto fijo dentro del `<title>`**, en vez del `pageHeader` condicional
   de 46 px que introdujo RF25 punto 7. Mismas palabras, misma fecha y mismo rojo, en una sola
   línea de 17 px; el sello diagonal del background no cambió. El motivo es geométrico, no
   estético: una banda aparte corría todo 46 px hacia abajo y desalineaba la grilla fija del
   talonario solo en las facturas anuladas.

Lo que **no** cambió, y no puede cambiar por una mejora de diseño: RUC, timbrado y su vigencia,
numeración `001-001-NNNNNNN`, fecha de emisión, datos del cliente, condición de venta, las tres
columnas Exentas / 5 % / 10 %, los subtotales y el cuadro de liquidación del IVA. Se reordenaron
y se reestilizaron; ninguno quedó afuera y ninguno cambió de número.

**Verificación de Joel en pantalla (12/10/2026, punto 10 de RF25): por qué REC-2026-000001 y la
factura 001-001-0000001 se reimprimen con "el diseño de las 08:14"**. Confirmado que es correcto
por diseño, no un error: D-94 ("generar al primer pedido y guardar", `obtenerOGenerarPdf`) cachea
el PDF la primera vez que se pide — toda reimpresión posterior de un recibo/factura **no
revertido/anulado** devuelve exactamente ese mismo PDF, byte a byte, nunca uno regenerado. Para
una factura (documento fiscal, DNIT) esto es exactamente lo que tiene que pasar: la reimpresión
de una factura real tiene que ser idéntica a la que se entregó, no "la versión más reciente del
diseño". La única excepción (RF25, hallazgo del mismo bloque) es cuando el recibo está
`REVERTIDO` o la factura está `ANULADA` — ahí sí se regenera siempre, porque el PDF viejo podía
mostrar un cobro ya anulado como si fuera válido (ver RF25 §2/§7).

**Consecuencia práctica**: como REC-2026-000001/001-001-0000001 ya se imprimieron (08:14, antes
de este bloque de correcciones), seguirán saliendo con el diseño VIEJO para siempre (correcto,
documento ya emitido) — el diseño NUEVO de `factura.jrxml` (sin la referencia de domicilio entre
paréntesis, tabla de montos corregida) nunca se había visto en pantalla por este motivo. Para que
Joel lo viera sin grabar nada en `sgcpc_db_pruebas` ni consumir numeración real, se generó una
MUESTRA aparte (`herramientas/muestras_rf23/factura_muestra.pdf`, datos de ejemplo: Mirtha,
interés 2.286 + IVA 229 Gs., número de factura y de operación marcados "(MUESTRA)") con
`GenerarMuestrasRF23Temporal` (`src/test/java/.../herramientas/`, no es un `*Test`, no corre con
`mvn test` — mismo patrón ya usado para la muestra del Lote 2). Mismo mecanismo para
`constancia_reversion_muestra.pdf` (RF25, puntos 1-6 ya corregidos). Ambas, y la clase temporal
que las genera, están en la lista de borrado de "Antes de entregar" (`AVANCE.md`).

**CORRECCIÓN (12/10/2026, urgente de Joel) sobre el párrafo anterior — retractado**: lo que
anoté arriba como "hallazgo en el código real" era, en realidad, un **error mío al armar la
muestra**, no un bug del servicio. `ServicioCobranza.emitirFactura` (código real, el que de
verdad emite una factura) calcula `totalGravado` sumando `LineaFactura.getTotal()` de cada línea
— y `LineaFactura.getTotal()` **ya es neto + IVA** por línea (ver su propio comentario: "lo que
suma a total_gravada_10"). Es decir, `factura.getTotalGravada10()` y `factura.getTotal()` SIEMPRE
son el total CON IVA incluido (2.515 en el ejemplo de Mirtha) — el código real nunca tuvo este
bug. El error estaba en `GenerarMuestrasRF23Temporal`: a mano, le pasé a `TOTAL_GRAVADA_10`/
`TOTAL`/`TOTAL_LETRAS` el interés NETO (2.286) en vez de neto+IVA (2.515), confundiendo una
variable local `totalGravado` de OTRO método (el cálculo, antes de emitir, de si corresponde
factura) con la de `emitirFactura` (incompatibles pese al mismo nombre). La factura real de las
08:14 (001-001-0000001) siempre mostró 2.515 en fila/subtotal/total, correctamente — como
confirmó Joel. **Corregido**: se extrajo el cálculo a un método público reusable,
`ServicioCobranza.calcularTotalesFactura(List<LineaFactura>)` (nuevo DTO
`dto.TotalesLineasFactura`), usado tanto por `emitirFactura` (real) como por la muestra (que ya
no arma los totales a mano) — con una prueba unitaria nueva
(`ServicioCobranzaTest.testCalcularTotalesFactura_TotalGravadoEsNetoMasIva`) que fija la regla:
total = subtotal 10 % = suma de las filas con IVA incluido. Muestra regenerada: ya dice 2.515 en
los tres lugares. De paso (pedido de Joel, mismo bloque, "si no cambia nada más del diseño
legal"): "TIMBRADO N°"/"FACTURA N°" → "N.º" en `factura.jrxml` — esto SÍ es un cambio al
`.jrxml` real (no solo a la muestra, que usa el mismo archivo), afecta toda factura nueva de acá
en adelante (ninguna ya emitida cambia, D-94).

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 27/10/2026 | **Rediseño de la factura como formulario de talonario** (§2.1 y §7): encabezado y datos del cliente en recuadros, tabla con todas las líneas y verticales hasta el pie, totales en filas enmarcadas dentro de la tabla. Actividad económica desde `empresa.actividad` (parámetro `EMPRESA_ACTIVIDAD`, 1 línea en `ServicioCobranza.generarPdfFactura`; antes era el texto fijo "Préstamos personales"). "Total de la operación" con `$P{TOTAL}`. Ejemplares "ORIGINAL — CLIENTE" / "COPIA — ARCHIVO TRIBUTARIO". Franja de ANULADA de alto fijo dentro del `title`. `factura.jasper` recompilado; `mvn test` 800/0. Probado en pantalla el mismo día (§9). | `dbc0527` | — |
| 12/10/2026 | `factura.jrxml`: "N°" → "N.º" (TIMBRADO/FACTURA). `ServicioCobranza.calcularTotalesFactura` extraído (reusable, con prueba unitaria) tras un error de la muestra (no del servicio real) en los totales. Muestra regenerada. `mvn test` 705/0. | *(pendiente)* | — |
| 13/10/2026 | Análisis contra el anteproyecto (este documento). Sin cambios de código. | *(pendiente)* | — |
| 26/09/2026 (decisión de Joel, RN-25) | El capital pasa a documentarse solo en el recibo, no en la factura (antes sí aparecía) | — | — |
| D-89 (sin fecha exacta) | Numeración de factura correlativa por vigencia del timbrado, no por año calendario (distinto del recibo) | — | — |

| 29/10/2026 | **B83 + B58**: el **recibo** pasa al formulario de talonario (mismo patrón que la factura del 27/10: recuadros, grilla pareja, firmas enmarcadas) y los **ejemplares** de los tres documentos quedan con el mismo formato `TIPO — DESTINATARIO`, sin cambiar quién se queda cada uno. Solo presentación: ni un dato, ni un importe, ni una línea de Java. **Probado en pantalla el mismo día** con `REC-2026-000014`, y D-94 verificado con la reimpresión de `REC-2026-000013`, que conserva el diseño viejo. `mvn test` 844/844 | *(este commit)* | — |

| 30/10/2026 | **B58 cerrado**: el texto de ejemplar del **comprobante de desembolso** quedó **visto en pantalla** (era lo único que faltaba del 29/10, donde solo se había visto en la muestra en PNG). El gerente Ramón Benítez reimprimió `DES-2026-000004` a las 07:10 desde Reportes → Estado de cuenta: página 1 *ORIGINAL — ARCHIVO CAPITAL CRÉDITO*, página 2 *COPIA — CLIENTE*. El botón se lo habilitó **B94** ([RF17_comprobante_desembolso.md](RF17_comprobante_desembolso.md)), del mismo día. Ningún `.jrxml` cambió hoy | `8e25a1c` | — |

| 01/11/2026 | **C37 + B102**, los dos hallazgos del paso 15 b, resueltos y **probados en pantalla el 01/11/2026 19:40-19:50** con la **cancelación total** de `CC-2026-00004`: **`REC-2026-000016`** dice *"Son: OCHOCIENTOS CUARENTA Y CUATRO MIL GUARANÍES"* (el efectivo) con *"Aplicado: 844.004 Gs. · Redondeo: -4 Gs."*, detalle de las cuotas 2/6 a 6/6 y leyenda *"PRÉSTAMO CANCELADO"*; **`001-001-0000008`** trae **una sola línea**, *"Interés compensatorio cuota 2/6"* por 2.483 Gs. con IVA 226 (las cuotas 3 a 6 recibieron solo capital, que no se factura, RN-25). La dirección del cliente salió *"mburicao 444"*: dato viejo, sin migrar, esperado (B13 normaliza al guardar, no retroactivamente) | `e933d21` (C37), `d788572` (B102) | — |
| 30/10/2026 (paso 15 b) | **Recibo y factura del primer cobro de una cuota que vence hoy** (`CC-2026-00004`, cuota 1/6). **`REC-2026-000015`**: 158.479 / 20.118 / 0 / 0 / 2.012 / **180.609**, *"Efectivo recibido: 180.600 · Vuelto: 0 · Aplicado: 180.609 · Redondeo: -9"*, saldo 841.521, 5 pendientes, próxima 2/6 – 27/11/2026, ejemplares *ORIGINAL — CLIENTE* / *COPIA — ARCHIVO CAPITAL CRÉDITO* (B58). **`001-001-0000007`** (timbrado 16842795, vigente 01/09/2026-31/08/2027), contado, recibo asociado, **una sola línea gravada al 10 %** por 22.130 con IVA 2.012 (el capital no se factura, `fiscal.iva_capital = EXENTO`), ejemplares *ORIGINAL — CLIENTE* / *COPIA — ARCHIVO TRIBUTARIO*. **Dos hallazgos de texto, registrados y sin tocar**: **B101**, el *"Son:"* del recibo repite lo **aplicado** y no lo **recibido** (viene de D-141 + `APLICADO_LETRAS`); y **B102**, la línea de concepto de la factura está **redactada en dos lugares** — `construirLineasCuota` al emitir (*"Intereses cuota 1 — CC-2026-00004 — vto. 30/10/2026"*, solo para sumar) y `derivarLineasCuotaFactura` al imprimir (*"Interés compensatorio cuota 1/6"*, la que se ve), porque la factura **no guarda renglones** y se reconstruyen de `aplicaciones_pago` (D-93/D-94) | `48501b6` | — |

## 9. Pruebas realizadas en pantalla

**27/10/2026 (Joel) — rediseño de la factura: vistas previas revisadas y aprobadas.** Se
generaron tres muestras con datos de ejemplo marcados "(MUESTRA)", **sin leer ni escribir
ninguna base y sin consumir numeración de timbrado**, con
`GenerarMuestrasFacturaTemporal` (`src/test/java/.../herramientas/`, no matchea `*Test`, no
corre con `mvn test`; compila el `.jrxml` en memoria, así que no tocó `factura.jasper` hasta la
aprobación). Los PDF quedaron en `pruebas/factura_muestra/` (fuera de `sistema/`), y la clase y
la carpeta están en la lista de borrado de "Antes de entregar" (`AVANCE.md`):

| Muestra | Qué prueba | Resultado |
|---|---|---|
| 1 renglón | El caso normal, y que las verticales bajen hasta el pie con un solo renglón | OK, 1 página |
| 11 renglones | Una cancelación total (una línea de interés por cuota), con una descripción de 4 líneas | OK, 1 página; las 6 celdas de esa fila crecen juntas |
| Anulada | Franja roja nueva + sello diagonal, y que la grilla no se desalinee | OK, geometría idéntica a la no anulada |

Las tres se revisaron como imagen (PNG exportado por el propio JasperReports con
`JRGraphics2DExporter`, sin instalar nada) buscando líneas cortadas, textos encimados o fuera de
su celda, columnas desalineadas y recuadros que tapen texto. **Un defecto encontrado y
corregido**: la palabra "FACTURA" no se imprimía. La celda tenía `height="16"` con fuente de
14 pt y **JasperReports no recorta un texto que no entra a lo alto: lo descarta entero**, sin
error ni warning (era un `staticText`). Corregido a 19 px, con el motivo comentado en el
`.jrxml` para que no se repita si alguien cambia el tamaño de fuente.

**27/10/2026 — factura emitida por el CÓDIGO REAL, con el diseño nuevo, el mismo día (DOGMA 1).**
La vista previa de más arriba no alcanza: muestra el `.jrxml`, no prueba que el circuito de
emisión produzca ese PDF. Y esperar a que haya interés devengado por cobrar está prohibido
(`REGLAS_DESARROLLO.md`, DOGMA 1). Se resolvió con una prueba automática que **no deja rastro**:
`PruebaEmisionFacturaPdf` (`src/test/java/.../servicios/`, **permanente**, no temporal).

Recorre el circuito real sobre `sgcpc_db_pruebas`, en **una sola transacción que después
revierte**: arma un pago con interés > 0 sobre CC-2026-00001 → `emitirFactura` (timbrado,
numerador, totales, `validarLineasFactura`, fila en `facturas`) → `reconstruirLineasFactura` →
`generarPdfFactura` contra el `factura.jasper` compilado → PDF.

| | |
|---|---|
| Factura emitida | **001-001-0000003**, total **25.802 Gs.**, IVA **2.346 Gs.**, cliente Joel Jesús Acosta |
| Verificado en el texto del PDF | "FACTURA", RUC 80123456-5, timbrado y vigencia, el número (calculado como numerador + 1, no escrito a mano), "Otorgante de Crédito Dinerario", fecha de emisión, condición de venta, moneda, tipo de transacción, operación, recibo, nombre y C.I. del cliente, la línea de interés, Exentas / 5 % / 10 %, subtotales, total de la operación, total en letras, liquidación del IVA, "ORIGINAL — CLIENTE" y "COPIA — ARCHIVO TRIBUTARIO" |
| Base antes y después | 2 facturas, 3 pagos de CC-2026-00001, numerador FACTURA en 2 — **idéntica**: el rollback devolvió el numerador de 3 a 2, **no se consumió ningún número de timbrado** |
| PDF | `pruebas/factura_muestra/factura_prueba_emision.pdf`, revisado y aprobado por Joel |

**Defecto encontrado por esta prueba y ahora vigilado**: en la primera corrida salió
"Recibo asociado: REC-PRUEBA-", **cortado**. Era culpa del dato de prueba (un número de recibo
mucho más largo que uno real), no del rediseño — pero mostró que esa celda **no estira y
recorta** en silencio. El dato pasó a tener el largo real (`REC-2026-957500`, como
`REC-2026-000002`) y la prueba **verifica el número completo**: si alguien angosta esa celda,
falla en vez de pasar con el dato cortado.

**Lo que esta prueba NO cubre**, y por qué: arranca en `emitirFactura`, no en `confirmarCobro`.
`confirmarCobro` y `obtenerPdfFactura` abren su **propia** conexión y hacen `commit()` (la
segunda, además, guarda el PDF en `archivos_binarios`), y `FabricaConexionBD` no tiene punto de
inyección — no hay forma de revertir lo que hacen. Así que el cobro en sí (`distribuirPago`, la
caja, la auditoría y el recibo) queda fuera.

**27/10/2026 13:21 (Joel), cajera Sofía Martínez, CC-2026-00003 — COBRO REAL CON FACTURA NUEVA,
EN PANTALLA. Cierra los dos pendientes que quedaban de este RF.** Es la pieza que ni la vista
previa ni `PruebaEmisionFacturaPdf` podían dar: el circuito completo, con caja, recibo y factura,
emitido por la aplicación y commiteado.

**Cómo se consiguió interés devengado sin esperar días** (DOGMA 1): CC-2026-00003 se había
desembolsado el 26/10, así que al día siguiente tenía **1 día de interés** — alcanza para que la
factura tenga un concepto gravado real, sin tocar ninguna fecha de la base.

Cobro por **"Por cantidad de cuotas", N = 1** — la vía nueva de RF27. Esta misma pasada está
registrada desde el lado de ese RF en `RF27_adelantar_capital.md` §9 ("séptima prueba"), donde es
la **primera** con factura por esa vía:

| En pantalla | Valor |
|---|---|
| Paga 1 cuota | **140.150 Gs.** |
| Cuotas que le quedan | 4 |
| Ahorro | 40.095 Gs. |
| Detalle del adelanto | interés devengado **532** + IVA **53**, capital **139.565**, saldo **679.835** |
| Cartel de confirmación | "Paga 1 cuota / Adelantar a capital: 140.150 / Recibido 140.150 / Sin vuelto" |
| Resultado | "Anticipo de capital aplicado (140.150 Gs.)" — Recibo **REC-2026-000008**, Factura **001-001-0000003** |

**La factura impresa, con el diseño nuevo de §2.1** — Joel revisó el PDF real, no una muestra:
encabezado enmarcado **con la actividad económica** (la decisión 1 de §7, el único cambio de Java
del rediseño, funcionando contra el parámetro `empresa.actividad`), "FACTURA" y el número
**001-001-0000003**, timbrado **16842795** con vigencia **01/09/2026 – 31/08/2027**, cliente con
su RUC/C.I., una sola línea — *"Interés compensatorio hasta el 27/10/2026 (anticipo de capital)"*
**585 Gs. en la columna 10 %** —, subtotales **0 / 0 / 585**, **"Total de la operación" 585 Gs.**
(decisión 2 de §7), en letras **QUINIENTOS OCHENTA Y CINCO GUARANÍES**, liquidación **IVA (10 %)
53** y **total IVA 53**, y los ejemplares **"ORIGINAL — CLIENTE" | "COPIA — ARCHIVO TRIBUTARIO"**
(decisión 4 de §7).

**Confirmado después contra `sgcpc_db_pruebas` (solo lectura)**, y todo cuadra con la pantalla:

| | |
|---|---|
| `pagos` | `id_pago` 11, `REC-2026-000008`, 27/10/2026 13:21:05, recibido 140.150, vuelto 0, aplicado 140.150, tipo `ANTICIPO` |
| `facturas` | `id_factura` 6, `numero` `0000003`, `fecha` 27/10/2026, `total_exenta` 0, **`total_gravada_10` 585**, **`iva_10` 53**, `total` 585, `EMITIDA`, `id_archivo` 47 |
| Aritmética | 532 + 53 = **585** = `total_gravada_10` (neto + IVA, la regla de §6d/§7) y 139.565 + 585 = **140.150** = lo recibido |
| Plan vigente de CC-2026-00003 | **4 cuotas** pendientes (eran 5); saldo tras la cuota 1 = 514.474, que con el capital de esa cuota da los **679.835** de pantalla |
| Numeradores | `RECIBO/2026` → 8 y `FACTURA/2026` → 3: **avanzaron de a uno**. Las secuencias internas de `pagos` y `facturas` sí tienen huecos (ids 8-10 y 3-5) — son las transacciones revertidas de `PruebaEmisionFacturaPdf`, y **confirman desde la base que ese rollback no consumió numeración de timbrado** |

**27/10/2026 22:19 — la primera factura REAL con varios renglones (7), con el diseño nuevo.**
Hasta acá todas las facturas reales habían tenido **un solo renglón**; los 11 renglones se habían
visto solo en una **muestra** (`GenerarMuestrasFacturaTemporal`, §9 más arriba), que prueba el
`.jrxml` pero no el circuito. La cancelación total de `CC-2026-00001` (ver
`RF26_cancelacion_anticipada.md` §9) emitió **001-001-0000006 con 7 renglones**: compensatorio,
moratorio y punitorio de las cuotas 1 y 2, más el compensatorio de la cuota 3.

| | |
|---|---|
| Total gravado al 10 % | **117.644** |
| IVA | **10.695** |
| Renglones | **7** |

**Lo que confirma, y que una muestra no puede confirmar**: la banda de detalle del rediseño
—con `stretchType="RelativeToTallestObject"` en sus 6 elementos (corrección 4 del 12/10/2026) y
las verticales bajando hasta el pie— **aguanta un documento real de varios renglones**, con las
descripciones largas del tipo *"Interés compensatorio…"* / *"Interés moratorio…"* por cuota, y la
banda de SUBTOTALES sigue alineada. Es también la primera factura real que lleva **moratorio y
punitorio** como conceptos gravados, cada uno con su IVA (RN-25).

Verificado contra la base: `facturas` número `0000006`, `total_gravada_10` 117.644, `iva_10`
10.695, `EMITIDA`. El IVA cierra concepto por concepto (4.659 + 566 + 170 + 4.182 + 127 + 38 +
953 = 10.695), no como un 10 % del total — que es la regla de §5 (el IVA se calcula sobre cada
concepto ya redondeado).

**Evidencia**: `evidencias\2026-10-27\facturas\001-001-0000006.pdf` y
`evidencias\2026-10-27\recibos\REC-2026-000012.pdf` (el recibo, con el detalle de las 9 cuotas).

**27/10/2026 — reimpresión de 001-001-0000002: sale el diseño VIEJO. D-94 funcionando.** Desde el
historial de cobros de CC-2026-00001 (la factura de las 06:32), la reimpresión devolvió el **PDF
guardado**, **idéntico al que se emitió ese día**, con el diseño anterior al rediseño — no se
regeneró nada contra el `factura.jasper` nuevo. Es la comprobación de que un documento ya emitido
nunca cambia de aspecto por un rediseño posterior: su PDF está en `archivos_binarios`
(`facturas.id_archivo = 45`, verificado). Con esto quedan conviviendo en la misma base, a
propósito, una factura con el diseño viejo (0000002) y una con el nuevo (0000003).

**Evidencia — las dos facturas, una al lado de la otra**, exportadas desde `archivos_binarios`
antes de la limpieza de la base (SHA-256 verificado contra la base, ningún PDF regenerado):

- `evidencias\2026-10-27\facturas\001-001-0000002.pdf` — **diseño VIEJO** (la reimpresa)
- `evidencias\2026-10-27\facturas\001-001-0000003.pdf` — **diseño NUEVO** (la emitida a las 13:21)
- `evidencias\2026-10-27\facturas\prueba_automatica_emision.pdf` — el PDF de
  `PruebaEmisionFacturaPdf`, cuya numeración se devolvió con el `rollback` (no es documento fiscal)
- `evidencias\2026-10-27\recibos\REC-2026-000008.pdf` — el recibo del mismo cobro

Abrir las dos primeras una al lado de la otra es la forma más directa de ver D-94 funcionando.
Ver `evidencias\2026-10-27\LEEME.md`.

**12/10/2026 (Joel), cajera, CC-2026-00001 (Mirtha)** — primer recibo y primera factura reales
de esta base de pruebas: REC-2026-000001 y factura 001-001-0000001 (emitidos desde "Adelantar a
capital", RF27 — ver `RF27_adelantar_capital.md` §9 para el detalle completo de esa prueba).
Confirmado leyendo `sgcpc_db_pruebas` (solo lectura): la factura lleva un solo concepto
(interés devengado 2.286 Gs. + IVA 229 Gs. = 2.515 Gs. de total gravado), sin el capital
(480.635 Gs., que solo quedó en `aplicaciones_pago` y en el recibo) — confirma en pantalla lo ya
descrito en §6d sobre que el capital nunca se factura.

**12/10/2026 (segunda vuelta)** — Joel revisó en pantalla el recibo REC-2026-000001 y la
factura 001-001-0000001 de la prueba anterior y pidió 4 correcciones, todas de cambio mínimo,
aprobadas e implementadas:
1. **Recibo — dos montos de cuota confusos**: mostraba "cuota 473.140 Gs. + IVA s/ interés" (la
   nueva cuota 1 sin IVA, `ANTICIPO_CUOTA_NUEVA`) y, aparte, "Próxima cuota: 24/11/2026 por
   482.025 Gs." (la misma cuota, con IVA, `ANTICIPO_PROXIMA_CUOTA_MONTO`) — dos números para el
   mismo concepto. Se sacó el primero; queda un solo monto, aclarado como "Próxima cuota (IVA
   incluido)" (`recibo.jrxml`).
2. **Recibo — textos**: "Firma del cajero" → "Firma de la cajera"; "N° " → "N.º " en el número
   de recibo (`recibo.jrxml`).
3. **Factura — domicilio del cliente con referencia coloquial**: `ServicioCobranza` ponía
   `cliente.getDomicilio()` tal cual (con la referencia entre paréntesis, p. ej. "casa blanca
   con portón verde") en `CLIENTE_DOMICILIO` — no corresponde en un documento legal (mismo
   criterio de B.15, ya aplicado en `ServicioDesembolso`/`ServicioFormalizacion`). Corregido en
   `ServicioCobranza.java:2085` con `ServicioClientes.domicilioSinReferencia`. El recibo no
   tiene el domicilio del cliente (no se imprime ahí), así que no tenía este defecto.
4. **Factura — tabla desarmada**: la descripción puede ocupar 2 renglones (p. ej. "Interés
   compensatorio hasta el 24/11/2026 (anticipo de capital)") y, sin `stretchType`, el texto se
   salía del recuadro de su celda mientras las celdas vecinas no crecían — la fila se veía
   rota y la banda SUBTOTALES (que por coordenadas sí estaba alineada) parecía no coincidir.
   Corregido agregando `stretchType="RelativeToTallestObject"` a los 6 elementos de la banda de
   detalle de `factura.jrxml`. Solo maquetación — ningún dato ni importe cambió.

Los 2 `.jrxml` tocados (`recibo.jrxml`, `factura.jrxml`) se recompilaron con
`herramientas/compilar_reportes.sh` (37 reportes, manifiesto `reportes_jasper.sha256`
actualizado); `mvn test` en verde, 692 pruebas (sin pruebas nuevas — son cambios de
presentación/maquetación y una corrección que reutiliza un método ya probado,
`domicilioSinReferencia`). **Falta la prueba en pantalla de estas 4 correcciones** — Joel la
hace con el Clean and Build.

**29/10/2026, Joel (cajera Sofía Martínez, 23:31-23:39) — B83 + B58, el recibo de talonario:**

- Adelanto de 228.050 sobre `CC-2026-00005` → **`REC-2026-000014`** con el diseño nuevo: recuadro
  de emisor, celda *"RECIBO DE DINERO / N.º REC-2026-000014 / Documento interno — no fiscal"*,
  datos del cobro en renglones enmarcados, cuerpo y firmas en recuadros, y al pie
  **ORIGINAL — CLIENTE / COPIA — ARCHIVO CAPITAL CRÉDITO**. Números correctos: próxima cuota
  30/11/2026 por 289.011, 10 cuotas, último vencimiento 30/08/2027, saldo 2.548.400.
- **D-94**: la reimpresión de `REC-2026-000013` sale con el **diseño viejo** y sus palabras viejas
  (*"ORIGINAL: Cliente / COPIA: Capital Crédito"*) — está guardado en `archivos_binarios`.
- **Queda anotado para B86** (decisión de Joel: no se toca ahora): el espacio vacío dentro del
  recuadro del anticipo, entre el saldo y "Efectivo recibido". Es la altura fija de esa rama.
- **Pendiente de verse en pantalla**: el comprobante de desembolso con las palabras nuevas (se
  reimprime desde *Reportes → Estado de cuenta → "Comprobante de desembolso"*). En la muestra se
  vio correcto.

## 10. Pendientes y trabajo futuro

- **Revisión legal de la factura** (ya anotada en `AVANCE.md`, "Antes de entregar"): un
  contador/abogado debe confirmar que el autoimpresor cumple toda la normativa vigente antes de
  usarse con clientes reales. No se toca nada de la factura sin esa revisión y sin la aprobación
  explícita de Joel.
- **(12/10/2026) Datos obligatorios de una factura de autoimpresor (DNIT)**: analizado a pedido
  de Joel — el proyecto NO tiene, en ningún documento, la cita de norma + artículo específico
  (Doc 00/R-02 ya deja esto como riesgo abierto: "IVA sobre intereses y formato de factura no
  confirmados por contador"). Comparando contra la lista general de datos que suele exigirse
  (sin poder citar el artículo exacto), el único faltante estructural encontrado es la **firma
  y aclaración del emisor** — no hay ninguna línea de firma en `factura.jrxml`. No se agregó
  nada (solo análisis, pedido explícito de Joel); antes de tocarlo hace falta la revisión de un
  contador (ver punto anterior).
- **Diseño completo del `recibo.jrxml`**: no revisado campo por campo en esta pasada — si Joel
  lo pide, se analiza con el mismo detalle que la factura.
- **(13/10/2026, Joel) Numeración de recibos y facturas en `sgcpc_db`**: la prueba de hoy
  consumió `REC-2026-000001` y `001-001-0000001` en `sgcpc_db_pruebas` — antes de entregar, la
  numeración en la base real debe arrancar en 1 (anotado también en `AVANCE.md`, "Antes de
  entregar").
- **Reprueba formal contra el texto del RF23**: diferida, parte con fechas reales (un cobro con
  conceptos gravados de verdad) — decisión de Joel, al final.
- **(12/10/2026) `ServicioConsultaCrediticia` con el mismo defecto de domicilio**: ver
  `RF22_cobro_efectivo.md` §10 — no se tocó, es de otro RF.

## 11. Guion para la defensa

1. Cobrar una cuota vencida con intereses y mora — mostrar el recibo (con capital) y la factura
   (sin capital, solo intereses/moratorio/punitorio con su IVA).
2. Señalar en la factura: timbrado y vigencia, número completo (001-001-0000001), RUC/C.I. del
   cliente, liquidación del IVA.
3. Explicar por qué es autoimpresor y no SIFEN (decisión documentada desde el anteproyecto,
   Doc. 00, no una limitación encontrada ahora).
4. (Si hay tiempo) mostrar una reversión y la factura quedando `ANULADA` con su nota de
   anulación, sin que se borre nada.
