# Lote 2 — Estilo común de PDF, análisis y muestra

*Las referencias a codeudor/garante de este documento quedaron superadas por el retiro de
codeudor/garante (18/10/2026, commit `6e7da13`) — ver `docs/funcionamiento/RF12_documentos_pdf.md`
y `RF24_seguimiento_mora.md`. Registro histórico, sin reescribir.*

**Estado (15/10/2026): L2-B (constancias de cancelación/devolución/reversión), L2-A (recibo/
factura/nota de anulación/comprobante de desembolso/arqueo de caja), L2-C (pagaré/plan de pagos/
contrato/autorización del buró/cotización) y L2-D piloto + D-1 (morosidad, cobros, cartera,
vencimientos, resumen mensual, resumen financiero) APLICADOS y APROBADOS por Joel, probados en
pantalla con datos reales — ver "Piloto L2-B", "L2-A", "L2-C" y "L2-D" más abajo. El resto de los
informes (grupos D-2 a D-5, PA-24/PA-26) y `mora/lista.jsp` (tanda 3) siguen sin tocar — ver
`AVANCE.md`, sección "Pendientes para la etapa de uso diario".**

**Estado original (11/10/2026, segunda vuelta): SOLO ANÁLISIS, ningún `.jrxml` real tocado.**
Pedido de Joel: proponer el estilo común (C.17 del plan de consolidación) aplicado como MUESTRA a
`plan_pagos.jrxml`, `pagare.jrxml`, `arqueo_caja.jrxml` y `comprobante_desembolso.jrxml`,
describir cómo quedaría cada uno, y generar un PDF de ejemplo. `factura.jrxml` no se toca. A la
espera de la aprobación de Joel para programarlo (ninguno de los 4 reportes reales fue editado).

## Piloto L2-B — aplicado y aprobado (14/10/2026)

Encabezado unificado vía subreporte común `src/main/resources/reportes/encabezado_constancia.jrxml`
(logo + datos de empresa + franja `#0C325C` + título en ese azul + "N.º"), reutilizado por
`constancia_cancelacion.jrxml`, `constancia_devolucion.jrxml` y `constancia_reversion.jrxml` vía
`<subreportExpression>` cargado del classpath del webapp
(`Thread.currentThread().getContextClassLoader().getResourceAsStream("reportes/encabezado_constancia.jasper")`)
— **no requiere pasar `SUBREPORT_DIR` ni ningún parámetro nuevo desde Java**: el título/N.º se
pasan como `subreportParameter` con un literal o el propio parámetro que cada maestro ya recibía.
Pie unificado ("Emitido por: usuario — fecha hora" + "Página X de Y") en un `pageFooter` nuevo en
los 3 maestros, con la corrección de PA-10 (ver abajo). 3 correcciones de Joel tras la primera
muestra: PA-10 (espacio de más en "Página X de Y"), dirección de la empresa en 1 sola línea
(ensanchar + letra más chica), y 2 espacios en blanco grandes (cajas de párrafo sobredimensionadas
frente al texto real, ya existentes antes del restyle, achicadas sin tocar ningún texto). Commit
del piloto, tag `lote2-B-probado`.

### PA-10 — causa real y corrección (sirve para toda futura tanda)

"Página 1 de    1" con espacio de más: el campo del total, alineado a la **derecha** en una caja
de 14pt, dejaba hueco a su izquierda cuando el número no llena la caja; y el campo "Página N de "
(con espacio final), también alineado a la derecha, empujaba ese espacio hacia adentro — dos
huecos sumados. Corrección: "de" **sin** espacio final (termina pegado al borde) + el campo del
total alineado a la **izquierda** con su propio espacio inicial (`" " + número`), pegado justo
donde termina "de". Confirmado con `pdftotext -layout` en las 3 muestras: "Página 1 de 1".

### Regla D-94 confirmada con un caso real (14/10/2026): "datos de empresa vigentes a la fecha del DOCUMENTO, nunca la de hoy"

Los 5 comprobantes que reimprimen datos de empresa (recibo, factura, constancia de cancelación/
devolución/reversión, nota de anulación) usan `parametrosEmpresaAFecha`/`parametrosEmpresaAFecha`
equivalente (`ServicioCobranza.java:1823-1836`, `ServicioFormalizacion.java` análogo) con la fecha
**del propio documento** (nunca `Reloj.hoy()`). Los documentos que NUNCA se guardan/reimprimen
(cotización, RF-33) usan en cambio el valor **vigente hoy** (`GeneradorInformesPdf.parametrosBase`,
líneas 58-67, vía `ServicioParametros.obtenerValor`, sin fecha).

Caso real: Joel cambió `empresa.ruc` de `80123456-7` a `80123456-5` el 14/10/2026 10:10 (con
motivo, desde `/parametros`). Mismo día:
- **Constancia de reversión REV-2026-000003** (`/reversiones` → Historial → Constancia), cuya
  `fecha_hora_reversion` es **12/10/2026** (antes del cambio): salió con RUC **80123456-7** — el
  vigente en esa fecha (`ParametroDAO.obtenerVigenteAFecha`, `vigente_desde <= fecha AND
  (vigente_hasta IS NULL OR vigente_hasta > fecha)`; la fila vieja tiene `vigente_hasta =
  2026-10-14`, exactamente el `vigente_desde` de la nueva, sin superposición). **Correcto.**
- **Cotización generada a las 14/10/2026 10:24** (después del cambio): salió con RUC
  **80123456-5** — el vigente hoy, sin fecha de documento (RF-33, la cotización nunca se guarda).
  **Correcto.**

Confirma que no hay ningún caché en memoria de parámetros (se descartó leyendo
`ServicioParametros`/`ParametroDAO`: cada llamada ejecuta un `SELECT` nuevo sobre la `Connection`
recibida, sin ningún `static Map`) — el comportamiento es 100 % por diseño (D-94), no un error.
Ver también `RF39_parametros.md` §9.

**Corrección sobre la primera muestra (11/10/2026)**: Joel encontró 8 puntos a corregir (a-h) —
el principal, datos de la empresa inventados en vez de reales ("eso no puede pasar en ningún
reporte"). La muestra fue regenerada de punta a punta con todas las correcciones; ver el detalle
en "Muestra generada (corregida)" más abajo. Se agregó además la muestra del PAGARÉ con el mismo
estilo, que no existía en la primera vuelta.

## L2-A — aplicado y aprobado (14/10/2026)

Estilo directo en cada archivo (sin subreporte común, por los distintos tipos de encabezado —
ver el análisis de la tanda), en `recibo.jrxml`, `factura.jrxml`, `nota_anulacion.jrxml`,
`comprobante_desembolso.jrxml` y `arqueo_caja.jrxml`: logo (ya llegaba por Java en todas las
llamadas a `llenarYExportar`, sin tocar código salvo los 2 puntos de abajo), franja `#0C325C`,
título en ese azul donde el documento ya tenía uno, pie "Emitido por: usuario — fecha hora" +
"Página X de Y" (con la corrección de PA-10: el campo del total alineado a la izquierda, pegado
donde termina "de"), y "N.º" en vez de "N°" en las etiquetas propias de cada documento.

**Único cambio de Java autorizado por Joel** (el resto fue solo `.jrxml`): `EMPRESA_WHATSAPP` en
`ServicioDesembolso.generarPdfComprobante` y `EMPRESA_DOMICILIO`/`EMPRESA_TELEFONO`/
`EMPRESA_WHATSAPP` en `ServicioCaja.generarPdfArqueo` — mismo patrón D-94 ya usado para el RUC
(`obtenerValorAFechaOMasAntiguo` con la fecha del propio documento), para que el encabezado de
ambos muestre los mismos datos que las constancias/recibo/factura. En `ServicioCobranza.
generarPdfFactura` se agregaron los parámetros NUEVOS `PIE_EMITIDO_POR`/`PIE_FECHA_HORA` (el
usuario que registró el pago y la fecha/hora real de ese registro, D-94 — nunca `Reloj.ahora()`)
para el pie "Emitido por"; `FECHA_EMISION` (dato legal del cuerpo, "Fecha de emisión") no se tocó.

**Por archivo, lo específico de cada uno:**
- **Recibo**: mantiene su diseño (N.º en un recuadro a la derecha, ahora en `#0C325C`); "Fecha: "
  pasó a "Asunción, " sola en su línea (antes compartía renglón con "Operación:").
- **Factura**: ningún dato legal se movió (timbrado, vigencia, RUC, N.º, condición de venta,
  liquidación del IVA, ORIGINAL/DUPLICADO) — solo se corrió el bloque de texto no legal de la
  izquierda para dejarle lugar al logo; el recuadro fiscal de la derecha quedó intacto. Pie
  "Emitido por" agregado (antes no existía: factura no pasaba un usuario emisor).
- **Nota de anulación**: mismo criterio que factura (2 columnas, recuadro fiscal TIMBRADO/NOTA
  N.º intacto); la cita "RG N° 118/2017" (DNIT) se dejó exactamente como está, por ser el nombre
  textual de la resolución, no una convención propia.
- **Comprobante de desembolso**: estilo aplicado igual en las 2 copias (ORIGINAL y COPIA); gap
  entre "N.º DES-…" y "Asunción," reducido (antes reservaba espacio fijo para la marca de
  reimpresión, que casi nunca imprime).
- **Arqueo de caja**: además del estilo común, 3 correcciones propias — (1) signo doble en
  "Vueltos entregados"/"Desembolsos"/"Compensaciones por reversión" (la etiqueta ya tenía "(−)",
  se quitó el "-" repetido del monto); (2) "Cajero/a" → "Cajera", "Sesión N°" → "Sesión N.º"; (3)
  huecos en blanco cuando no hay redondeo/motivo de diferencia, con `isRemoveLineWhenBlank` +
  `positionType="Float"` en los campos condicionales y en las franjas siguientes (mismo criterio
  en 3 lugares: redondeo, motivo de la diferencia, y el margen final de la banda antes de
  "DETALLE DE MOVIMIENTOS"). Hallazgo aparte: el pie vivía en `<summary>` (imprime una sola vez,
  al final de TODO el documento) — en una sesión de 2+ páginas, la página 1 quedaba sin pie;
  movido a un `<pageFooter>` real (se repite en cada página).

Probado en pantalla por Joel (14/10/2026) con datos reales: cobro CC-2026-00029 (recibo
REC-2026-000022 + factura 001-001-0000015) y arqueo de la sesión 18. Aprobado.

## L2-C — aplicado y aprobado (14/10/2026)

Estilo directo en cada archivo (igual que L2-A, sin subreporte, por los distintos tipos de
encabezado), en `cotizacion.jrxml`, `autorizacion_buro.jrxml`, `pagare.jrxml`, `plan_pagos.jrxml`
y `contrato.jrxml`: franja `#0C325C` en vez de la línea verde o fina negra que tenía cada uno,
título en ese azul en el mismo lugar donde ya estaba, recuadro de N.º de operación/sesión gris
`#E8E8E8` → azul `#0C325C` con letra blanca (donde lo tenía: pagaré, plan de pagos, contrato —
igual que ya tenían recibo/factura/constancias desde L2-A/L2-B), "N.°"/"N°" → "N.º", y la
corrección de PA-10 en el pie ("Página X de Y") en los 5 archivos. `pagare.jrxml`/`plan_pagos.jrxml`
no tenían logo: se agregó (mismo `<image>` 42×42 que el resto), con el letterhead corrido a
`x="50"` para dejarle lugar. `autorizacion_buro.jrxml` no tenía pie: se agregó un `<pageFooter>`
nuevo ("Emitido por"/"Página X de Y"), igual que arqueo de caja en L2-A. El "PAGARÉ A LA ORDEN"
del cuerpo de `pagare.jrxml` se dejó en su posición original, solo pasó a azul (pedido explícito
de Joel). El hueco de PA-09 (banda `pageHeader` sin `printWhenExpression` propio, reserva su
altura declarada aunque esté oculta en la página 1) **no se tocó** en ninguno de los 3 archivos
que lo tienen (`contrato.jrxml`, `plan_pagos.jrxml`, y ahora también se nota en `pagare.jrxml`
tras agregarle el logo) — queda para la solución de raíz de PA-11 (subreporte común de
encabezado). Hallazgo menor anotado en PA-09: en `contrato.jrxml`, página 2 en adelante, la
cláusula "QUINTA — Mora." queda pegada justo debajo de la franja repetida.

### Corrección de Java: datos de empresa y fecha del pie, según la fecha del DOCUMENTO (D-94)

Auditoría de Joel sobre la muestra de `05_contrato.pdf`/`01_pagare.pdf`/`02_plan_pagos.pdf`:
encontró que `ServicioFormalizacion.construirParametrosReporte` (privado, compartido por
`generarPdfContrato`/`generarPdfPagare`/`generarPdfPlanDePagos`) leía los 8 `EMPRESA_*`
(razón social, nombre comercial, RUC, domicilio, teléfono, WhatsApp, correo, registro BCP,
representante legal) con `ServicioParametros.obtenerValor` (valor **vigente hoy**) en vez de
`obtenerValorAFechaOMasAntiguo` con la fecha de formalización — y `FECHA_EMISION` con
`Reloj.ahora()` en vez de la fecha de formalización. Mismo bug, mismo patrón, en
`ServicioConsultaCrediticia.construirPdfAutorizacionBuro` (los 5 `EMPRESA_*`, vía
`GeneradorInformesPdf.parametrosBase`, que también usa `obtenerValor`).

Caso real que lo expuso: el RUC de la empresa cambió de `80123456-7` a `80123456-5` el 14/10/2026
(corrección del dígito verificador, D-94 ya documentado como pendiente en `AVANCE.md` antes de
esta fecha) — una reimpresión del pagaré/plan/contrato de un préstamo formalizado el 11-12/10
mostraba el RUC **nuevo** (-5) en vez del que estaba vigente el día que el cliente firmó (-7).

**Corrección aplicada** (mismo patrón ya usado por `ServicioCobranza.parametrosEmpresaAFecha` y
`ServicioDesembolso`/`ServicioCaja`, todos desde D-94/E8 original):
- `ServicioFormalizacion.construirParametrosReporte`: los 8 `EMPRESA_*` con
  `obtenerValorAFechaOMasAntiguo(conexion, "empresa.X", prestamo.getFechaFormalizacion().toLocalDate())`;
  `FECHA_EMISION` con `prestamo.getFechaFormalizacion()`. Afecta a los 3 (contrato/pagaré/plan
  de pagos, que comparten el método) — se corrigió en la raíz (no en cada llamador) porque
  `contrato.jrxml` solo usa `FECHA_EMISION` en el pie ("Emitido por"), nunca en el cuerpo
  (verificado antes de tocar: ningún "lugar y fecha" del contrato depende de ese parámetro).
- `ServicioConsultaCrediticia.construirPdfAutorizacionBuro`: los 5 `EMPRESA_*` con
  `obtenerValorAFechaOMasAntiguo(conexion, "empresa.X", fechaHoraEmision.toLocalDate())`, después
  de `parametrosBase` — sin tocar `parametrosBase`/`GeneradorInformesPdf` (lo comparten otros
  informes de gestión que sí deben mostrar los datos de HOY, por diseño: ver más abajo).
- **Auditados, sin el problema** (ya se confirmó que no les aplica): los 24 `generarInforme*Pdf`
  de `ServicioInformes` (informes de gestión/análisis, siempre "a la fecha de hoy" por diseño,
  nunca reimpresión de un documento firmado), `generarInformeCotizacionPdf` y
  `ServicioClientes.generarInformeRequisitosPdf` (ninguno de los dos se guarda ni se reimprime,
  mismo criterio ya decidido para la cotización — ver la sección de D-94 más arriba).
- Verificado con `pdftotext -layout` antes/después en pagaré, plan de pagos y contrato de un
  préstamo real (CC-2026-00001, formalizado 11/10 con RUC -7 vigente entonces): la única
  diferencia de texto es el RUC (y, en el contrato, la fecha del pie "Emitido por", que antes no
  tenía el fix de L2-C) — ninguna cláusula, monto ni nombre cambió.

Probado en pantalla por Joel (14/10/2026) con datos reales: CC-2026-00003 (contrato, pagaré y
plan de pagos re-emitidos vía "Re-emitir los PDF") con RUC -7 y pie "12/10/2026 09:19" (fecha de
formalización real de ese préstamo, no la de hoy); cotización nueva con RUC -5 (vigente hoy,
correcto — nunca se guarda). Aprobado.

**Pendiente anotado, no implementado en esta ronda** (ver PA-31 en `AVANCE.md`): ninguna de las 3
rutas de regeneración de PDF (`regenerarSoloPdfs`/`regenerarDocumentos`/`generarPdfsFaltantes`)
valida en el servidor que el préstamo siga `PENDIENTE_DESEMBOLSO` — hoy el único freno es que la
ficha oculta esos botones. Verificado por `SELECT` que no hay ningún caso real afectado en
`sgcpc_db_pruebas` hoy.

## L2-D — piloto (morosidad/cobros) + D-1, aplicado y aprobado (15/10/2026)

A diferencia de L2-A/L2-C (estilo directo, sin subreporte, por los distintos tipos de encabezado
de cada comprobante), los 25 `informe_*.jrxml`/`estado_cuenta.jrxml` del Bloque Informes comparten
el mismo tipo de encabezado — se auditaron los 25 completos y se agrupó el trabajo en 5 tandas
(D-1 a D-5, ver `AVANCE.md`), empezando por un piloto de 2 archivos antes de extender el molde al
resto de D-1.

### Encabezado común — `encabezado_informe.jrxml` (subreporte, nuevo)

Mismo mecanismo que `encabezado_constancia.jrxml` de L2-B: `<subreport>` con
`JREmptyDataSource(1)` y `<subreportExpression class="java.io.InputStream">` cargado del classpath
del webapp (`getResourceAsStream("reportes/encabezado_informe.jasper")`) — sin pasar `SUBREPORT_DIR`
ni tocar la firma Java de ningún `generarInforme*Pdf`. Contenido: logo (42×42) + 3 líneas
(razón social — RUC / domicilio / "Tel. · WhatsApp") + franja `#0C325C` + título centrado en ese
azul, recibido como parámetro (`TITULO`) para poder reutilizarse en los 6 informes. **Sin pie
dentro del subreporte, a propósito**: `evaluationTime="Report"` (necesario para la "Y" de "Página
X de Y") se resuelve contra el reporte que se está llenando — un subreporte invocado desde el
`pageFooter` del padre se llena aparte, así que su propio `evaluationTime="Report"` apuntaría a sí
mismo, no al padre. El pie sigue viviendo directo en el `pageFooter` de cada informe (igual que
L2-A/L2-B/L2-C).

`GeneradorInformesPdf.parametrosBase` (compartido por los 24 `generarInforme*Pdf` de
`ServicioInformes`) se extendió con `EMPRESA_TELEFONO`/`EMPRESA_WHATSAPP` — confirmado seguro para
los 3 consumidores que ya sobrescribían esas 2 claves localmente (`construirPdfAutorizacionBuro`,
`generarInformeRequisitosPdf`, `generarInformeCotizacionPdf`).

### Piloto — `informe_morosidad.jrxml` + `informe_cobros.jrxml`, 3 rondas de ajuste de Joel

1. Encabezado/pie aprobados sin cambios la primera vuelta.
2. Se sacó la línea "Emitido: fecha · Usuario: nombre · Préstamos en mora: N" (repetía el pie) —
   queda solo el dato propio del informe ("Préstamos en mora: N" / "Cantidad de cobros: N") en el
   mismo renglón que "Filtros aplicados", sin línea vacía entre ese renglón y la tabla.
3. Columnas redistribuidas donde el texto se cortaba en 2 renglones o usaba letra más chica que el
   resto (morosidad: "Monto exigible" angostada a lo necesario, ese espacio pasado a "Cliente";
   "Cuotas venc." → "Cuotas vencidas" en una línea, mismo tamaño de letra que el resto de los
   títulos de columna; cobros: Cliente/Tipo ensanchadas). **Regla confirmada por Joel para textos
   muy largos** (nombre de cliente, "Tipo — Revertido"): está bien que pasen a 2 renglones, con la
   MISMA letra que el resto (nunca achicar) y la fila creciendo en alto (`isStretchWithOverflow`) —
   nunca se corta ni se superpone texto. No se siguió apretando columnas para eliminar esos casos
   límite (decisión explícita de Joel). La columna "Codeudor" de la rama inactiva
   `GARANTE_HABILITADO=true` en morosidad se angostó también (140→60pt) para no romper la fila —
   código muerto confirmado (`credito.exigir_garante = "nunca"`, D9), queda anotado en
   "Pendientes para la etapa de uso diario" de `AVANCE.md`, sin tocar más.

Aprobado por Joel tal como quedó. Sin commit en ese momento — se esperó a extender el mismo molde
al resto de D-1 antes de probar todo junto en pantalla.

### Resto de D-1 — `informe_cartera.jrxml`, `informe_vencimientos.jrxml`, `informe_resumen_mensual.jrxml`, `informe_resumen_financiero.jrxml`

Mismo molde del piloto en los 4. Ajustes propios de cada uno:

- **Cartera**: encabezado "Interés futuro programado (no exigible, Ley 6624)" 6.5→7pt (ya tenía
  ancho de sobra). Sin otros problemas de columna.
- **Vencimientos**: sin cambios de columna (ya uniforme en 7pt). **Hallazgo de Joel (15/10/2026,
  consulta solo lectura sobre CC-2026-00029, cuota 2/6)**: la columna "Monto" muestra
  `cuotas.total_programado` (el total ORIGINAL de la cuota), no el saldo realmente pendiente —
  confirmado por `SELECT` (1.802.898 total vs. 1.633.603 pendiente, con 169.295 ya pagados en una
  cuota `PARCIAL`) y por código (`ServicioReportes.listarVencimientos`, línea 143). Joel pidió
  dejarlo anotado sin corregir — ver "Pendientes para la etapa de uso diario" en `AVANCE.md`.
- **Resumen mensual** — **PA-25**: `FormateadorFechas.comoMesYAnio(YearMonth)` (nuevo, reutiliza el
  array `MESES` ya existente) reemplaza la concatenación directa de `YearMonth.toString()`
  ("2026-10" → "octubre de 2026") en los 3 métodos que lo necesitaban
  (`construirTextoFiltrosResumenMensual`, `construirTextoFiltrosIva`,
  `construirTextoFiltrosFacturasAnuladas` — mismos 3 de PA-25/RF31/RF33). Solapamiento de 10pt
  entre "Saldo de préstamos en mora" y "Composición de lo cobrado" corregido corriendo el resto
  del contenido +10.
- **Resumen financiero** — **PA-25 + PA-30, la vuelta con más trabajo**: mismo fix de fecha en
  palabras. El solapamiento de PA-30 (pie contra "Demanda por tramo de plazo") llevó a 2 rediseños
  sucesivos antes de la versión aprobada:
  1. Primer intento: todo el contenido a una banda `detail` (en vez de `title`, que en
     JasperReports debe entrar completo en una sola página declarada — hallazgo nuevo de esta
     sesión) de una sola columna, para que pudiera paginar sola si hacía falta. Con los datos
     reales no entraba en A4: pasaba a una 2.ª página con una sola línea suelta y "Demanda por
     tramo de plazo" quedaba pegado al pie en la 1.ª — **rechazado por Joel**.
  2. Versión aprobada: `detail` en **dos columnas** de 370pt (izquierda: Créditos otorgados/en
     trámite, Aprobados sin desembolsar, Morosidad actual, Composición de lo cobrado con Total/
     Ganancia; derecha: Cartera, Demanda por tramo de monto, Demanda por tramo de plazo) — con los
     datos reales entra cómodo en una sola hoja A4, con margen antes del pie. De paso se corrigió
     que `DEMANDA_POR_MONTO_TEXTO`/`DEMANDA_POR_PLAZO_TEXTO` (un solo `textField` con líneas unidas
     por `"\n"`) usaban el interlineado por defecto de la fuente, más apretado que el resto del
     informe — `<paragraph lineSpacing="Fixed" lineSpacingSize="14"/>` lo iguala a los 14pt del
     resto. `detail` sigue pudiendo paginar sola si en el futuro hay muchos más tramos de demanda.

Probado en pantalla por Joel (15/10/2026) en el sistema real: los 6 informes (morosidad, cobros,
cartera, vencimientos, resumen mensual, resumen financiero) aprobados. Commit `lote2-D1-probado`.

## Corrección de un error de este análisis: "Página X de Y" por archivo

La primera versión de este documento decía que **ninguno** de los 4 reportes tiene "Página X de
Y". Es incorrecto para 2 de los 4 — corregido tras releer los `.jrxml` reales completos:

| Archivo | "Página X de Y" | "Emitido por: usuario — fecha" |
|---|---|---|
| `plan_pagos.jrxml` | **Ya lo tiene** (líneas 310-317) | Ya lo tiene (línea 303) |
| `pagare.jrxml` | **Ya lo tiene** (líneas 160-181 aprox.) | Ya lo tiene (línea 165) |
| `arqueo_caja.jrxml` | No tiene | No tiene |
| `comprobante_desembolso.jrxml` | No tiene | Ya lo tiene (líneas 253/454, 2 páginas) |

Lo que sí falta de verdad en los 4 — y es el cambio real del pie —: unificar el texto a **"Impreso
por [usuario] el [fecha/hora]"** (hoy dice "Emitido por: X — Y", donde ya existe) y agregar
"Página X de Y" donde falta (`arqueo_caja.jrxml` y `comprobante_desembolso.jrxml`).

## Muestra generada (corregida)

Dos ejemplos reales, compilados y exportados a PDF con el estilo propuesto y las 8 correcciones
de Joel (a-h):

- **Plan de pagos**: `herramientas/muestras_lote2/plan_pagos_muestra.jrxml` (diseño) +
  `plan_pagos_muestra.pdf` (12 cuotas completas, no recortada — préstamo de ejemplo
  CC-2026-00099, Mirtha Lucía López Rojas, Gs. 2.000.000, sistema francés).
- **Pagaré**: `herramientas/muestras_lote2/pagare_muestra.jrxml` (diseño) +
  `pagare_muestra.pdf` (sin codeudor) + `pagare_muestra_codeudor.pdf` (con codeudor, para ver las
  2 variantes del texto y de las firmas).
- Los tres `.jrxml` viven fuera de `src/main/resources/reportes/` a propósito — no son parte del
  sistema, no se bundlean en el `.war`, no reemplazan ningún reporte real.
- Generados por `src/test/java/.../herramientas/GenerarMuestraLote2.java` (no es una prueba
  JUnit, no la corre `mvn test` — se ejecuta a mano). Los datos de la **empresa** (razón social,
  RUC, domicilio, teléfono, Registro BCP) se leen de verdad de `sgcpc_db_pruebas` vía
  `ServicioParametros`, igual que el contrato — nunca inventados (corrección a). El cliente, el
  préstamo y las 12 cuotas son datos de prueba fijos en el programa (no existen en la base).

Las 8 correcciones de Joel sobre la primera muestra, todas aplicadas:

- a) Datos de la empresa reales vía `$P{EMPRESA_*}`, leídos de Parámetros — ya no texto inventado.
- b) "N.º" del encabezado de la tabla en blanco sobre azul (antes quedaba en negro por un
  `<staticText>` duplicado sin `forecolor` superpuesto al de fondo azul — corregido).
- c) "Saldo según plan" con margen interno (`rightPadding="6"`) en vez de pegado al borde.
- d) Interés e IVA 10 % en columnas separadas (igual que el plan actual) — nunca se juntaron.
- e) Recuadro de datos con cliente/C.I., monto en números y letras, plazo, sistema francés,
  TEA/TNA/CFT, fecha de formalización **y primer/último vencimiento agregados**.
- f) Las 4 notas del plan actual, texto verbatim, sin cambios.
- g) Firmas en 2 bloques (línea + rol + "Aclaración: nombre — C.I." + "Fecha: __/__/____"):
  cliente ("Recibí copia de este plan de pagos") y quien atendió ("Entregado por").
- h) 12 cuotas reales de prueba, página completa, no recortada.

**Nota de implementación**: el espacio en blanco que podría quedar debajo del contenido en un
plan con menos de 12 cuotas se resuelve igual que en `contrato.jrxml`: bandas con
`positionType="Float"` en cascada — no es un problema nuevo de esta muestra.

## Estilo común propuesto (C.17)

- **Encabezado**: logo (42×46, `$P{LOGO}`, ya disponible vía `GeneradorInformesPdf.logoComoBytes()`,
  sin URL — D.22) a la izquierda; razón social/RUC/domicilio/teléfono a la derecha del logo (ya
  vienen de Parámetros, `EMPRESA_*`, en los 4 reportes).
- **Franja de color institucional**: una barra sólida de 5 px, azul de marca `#0C325C` (mismo
  color que ya usa SweetAlert2 en pantalla), debajo del letterhead, antes del título.
- **Título**: centrado, 14-15 pt, negrita, con el N.º de operación/sesión debajo en una línea
  más chica.
- **Recuadro de datos**: mismo estilo que ya tiene `contrato.jrxml` (caja gris clara `#F2F2F2`,
  texto con los datos clave) — se extiende a los 4.
- **Tablas** (plan de pagos, arqueo): encabezado con fondo azul institucional `#0C325C` y texto
  blanco en negrita (hoy es gris plano `#DCDCDC` con texto negro); filas alternadas blanco /
  celeste muy claro `#F0F4F8` para que se lean mejor con muchas filas; la columna de total (o el
  monto, en arqueo) en negrita.
- **Firmas**: bloques lado a lado (línea, NOMBRE DEL ROL en negrita centrado, "Aclaración:
  [nombre]", "C.I. [número]", "Fecha: [dd/mm/aaaa]") — hoy la mayoría de los reportes solo
  tienen una línea + nombre + C.I., sin aclaración explícita ni fecha.
- **Pie, en todos**: "Impreso por [usuario] el [fecha/hora]" a la izquierda + "Página X de Y" a
  la derecha, separados por una línea fina — hoy `plan_pagos.jrxml` y `pagare.jrxml` ya tienen
  ambos datos (con el texto "Emitido por: X — Y", a renombrar por consistencia);
  `arqueo_caja.jrxml` no tiene ninguno de los dos; `comprobante_desembolso.jrxml` tiene el
  usuario/fecha pero no "Página X de Y" (ver tabla de corrección más arriba). "Impreso por" en
  ese texto exacto no se encontró en ninguno de los 37 reportes del sistema (confirmado en el
  plan de consolidación C.17) — es el único nombre nuevo.
- **Espacios en blanco**: cambiar bandas de altura fija por `positionType="Float"` en cascada
  (mismo patrón ya probado en `contrato.jrxml`) en los 3 puntos confirmados (ver abajo, por
  archivo).

## Por archivo

### `plan_pagos.jrxml`

Hoy: sin logo, sin franja de color, título simple, recuadro de datos sin fondo, tabla con
encabezado gris plano (`#DCDCDC`), firma única ("Recibí copia...", una sola línea de texto, sin
aclaración/C.I./fecha separados — ya tiene la de "quien atendió" agregada en el Lote 1, también
como una sola línea de texto). **Ver la muestra generada** — aplica el estilo completo: logo,
franja, recuadro, tabla con encabezado azul y filas alternadas, total en negrita, y las 2 firmas
(cliente/quien atendió) como bloques separados con aclaración y fecha en vez de una sola línea
de texto corrida. Espacio en blanco (hallazgo de RF12 §10b): el título usa alturas fijas sin
`positionType="Float"` consistente — se corrige al mismo tiempo que el resto del rediseño.

### `pagare.jrxml`

Hoy: sin logo (nunca lo tuvo — RF12 §10e), con el mismo letterhead de 6 líneas que el contrato
pero sin la imagen ni franja de color, badges de título/número en gris `#E8E8E8`, un solo bloque
de texto para el pagaré (sin tabla), firmas de deudor/codeudor ya con línea+nombre+C.I. (sin
aclaración explícita ni fecha). Aplicando el estilo: agregar el `<image>` del logo (mismo
`x=0,y=0,width=42,height=42` que el contrato), reemplazar los badges grises por la franja azul +
badge con fondo institucional y texto blanco, agregar "Fecha:" debajo de cada firma. El cuerpo
del pagaré (texto corrido, sin tabla) no cambia de estructura — es la cláusula que exige el
anteproyecto, no una tabla de datos.

### `arqueo_caja.jrxml`

Hoy: sin logo, "Sesión N°" (falta el `.º`), "Cajero/a" (en vez de "Cajera"), encabezados de
columna de "DETALLE DE MOVIMIENTOS" **inexistentes** (la banda `columnHeader` solo tiene el
título de sección "DETALLE DE MOVIMIENTOS" — confirmado en el código, líneas ~353-360 — nunca
imprime "Hora"/"Tipo"/"Operación"/"Comprobante"/"Monto" como encabezado de columna, aunque los 5
campos ya existen y ya vienen en ese orden exacto), "DIFERENCIA" como texto plano sin ningún
color ni recuadro (confirmado, líneas ~214-221, ningún `backcolor`), "COMPOSICIÓN DE LO
COBRADO" siempre con sus 5 líneas (capital/interés/moratorio/punitorio/IVA) aunque todas sean 0
cuando no hubo cobros en la sesión. **C.18, todos cambios reales, no solo estéticos**:
- Agregar la fila de encabezados de columna ("Hora", "Tipo", "Operación", "Comprobante",
  "Monto") que hoy no existe, con el mismo fondo azul institucional del resto de las tablas.
- "DIFERENCIA" en un recuadro: ámbar (`#a15c00`, mismo tono que ya usa la pantalla) si sobra,
  rojo si falta — hoy es texto plano sin distinción visual.
- "Composición de lo cobrado" en una sola línea ("Sin cobros en esta sesión.") cuando los 5
  montos son cero, en vez de imprimir las 5 líneas vacías.
- "Cajero/a" → "Cajera" (texto fijo).
- "N°" → "N.º" (2 apariciones: título y pie de la página de continuación).
- Firmas con aclaración + fecha (ya existen las firmas, falta agregar esas 2 líneas).
- Logo + franja + recuadro de datos (igual que los otros 3).

### `comprobante_desembolso.jrxml`

Hoy: sin logo, franja ausente, usa el mismo patrón de 2 páginas (ORIGINAL/COPIA) con una banda
de **título de altura fija = 600** (confirmado, línea 56) — el contenido real ocupa bastante
menos, de ahí el espacio en blanco grande que señaló Joel en RF17 §10e. Firmas de cliente/cajera
ya existen (sin aclaración/fecha explícitas). Aplicando el estilo: logo + franja + recuadro de
datos del préstamo (mismo patrón), encabezado de página con fondo institucional en vez de solo
texto alineado a la derecha ("ORIGINAL – Empresa"/"COPIA – Cliente"), firmas con aclaración y
fecha, y **la altura fija de 600 pasa a bandas con `positionType="Float"`** (mismo criterio que
el resto) para que el espacio en blanco desaparezca — se mantienen las 2 páginas (ORIGINAL y
COPIA), cada una con su propio ajuste.

## Lo que NO cambia

- **`factura.jrxml`**: fuera de este punto por completo (formato legal, Joel lo revisa aparte),
  ni siquiera el encabezado compartido.
- **Ninguna librería ni dependencia nueva**: el logo ya se lee del classpath
  (`GeneradorInformesPdf`), los colores son valores hexadecimales directos en el `.jrxml` (mismo
  mecanismo que ya usan los 37 reportes), "Página X de Y" usa las variables incorporadas de
  JasperReports (`$V{PAGE_NUMBER}`, ya usadas en los 24 informes) — nada de esto requiere un jar
  nuevo.
- **El contenido/los datos de cada reporte no cambian** — solo la presentación (colores,
  encabezados, pie, alturas de banda). Ningún cálculo nuevo, ninguna cláusula legal se toca.

## Cómo seguiría (si Joel aprueba)

1. Aplicar el estilo real a los 4 `.jrxml` (no la muestra, los archivos de
   `src/main/resources/reportes/`), reutilizando los mismos valores de color/posición ya
   validados en la muestra.
2. `herramientas/compilar_reportes.sh` (recompila y actualiza el hash — control permanente ya
   en marcha desde el Lote 1).
3. `mvn test` (`ReportesCompiladosTest` incluido).
4. Prueba en pantalla con Joel: contrato de CC-2026-00002, plan de pagos, pagaré, un desembolso
   y un cierre de caja con diferencia, para ver los 4 reportes reales con el estilo nuevo.
5. Recién con eso aprobado, extender el mismo estilo al resto de los reportes del sistema (los
   otros 32, sin contar `factura.jrxml`) — alcance explícitamente fuera de este Lote 2.
