# Escena 05 — Desembolso

Qué cubre: la bandeja de préstamos listos para desembolsar, entrega de efectivo adicional del
gerente, condiciones del desembolso (checklist y documentos vigentes), comprobante antes de
entregar, y los movimientos de caja resultantes. La apertura de caja se cubre en
[00_apertura.md](00_apertura.md).

**Tramo 4 del recorrido (continuación), probado en pantalla por Joel el 21/10/2026, rol
cajera, con la caja ya abierta.** Cliente de ejemplo ficticio: **Marta Ovelar (C.I. 1.000.001)**,
préstamo `CC-2026-NNNNN` listo para desembolsar (checklist completo desde
[04_formalizacion.md](04_formalizacion.md)).

## Paso 1 — Bandeja de préstamos listos para desembolsar: pantalla /desembolso (RF16)

- **Usuario/rol**: Cajera.
- **Qué hace el usuario**: toca **"Desembolsos"** (menú "Desembolso y caja") o **"Ir a
  Desembolsos"** desde el panel (ver [00_apertura.md](00_apertura.md) Paso 4). Se abre
  `GET /desembolso`.
- **Qué responde el sistema**: título **"Préstamos listos para desembolsar"**; buscador con un
  solo campo, **"N.º de operación, cédula o nombre del cliente"**, y los botones **"Limpiar"**/
  **"Buscar"** (mismo mecanismo de listado vivo que Clientes/Mora/Aprobaciones/Solicitudes/
  Formalización); tabla con columnas **Operación, Cliente** (nombre y C.I.), **Monto
  aprobado, Formalizado** (fecha y hora) y un botón **"Desembolsar"** por fila — hoy, una sola
  fila: `CC-2026-NNNNN`, Marta Ovelar.
- **Regla de negocio**: ver [RF16_condiciones_desembolso.md](../funcionamiento/RF16_condiciones_desembolso.md).
- **Qué pasa por detrás, verificado en el código (solo lectura)**:
  - **Método que arma la lista**: `ControladorDesembolsoServlet.mostrarLista` →
    `ServicioFormalizacion.listarListosParaDesembolsar` — internamente,
    `PrestamoDAO.listarPendientesDesembolso` (`estado_prestamo = 'PENDIENTE_DESEMBOLSO' ORDER
    BY fecha_formalizacion`) filtrado por `esChecklistCompleto`/`obtenerChecklist` (el mismo
    método ya confirmado en el tramo 3 y en el panel de la cajera).
  - **Orden**: por `fecha_formalizacion` ascendente — el préstamo formalizado hace más tiempo
    aparece primero (orden de llegada, no alfabético ni por monto).
  - **Buscador**: `ServicioFormalizacion.filtrarPrestamosPorTexto`, en memoria, sobre la lista
    ya filtrada — mismo patrón normalizado (sin acentos/mayúsculas) que el resto del sistema.
  - **Aviso adicional, no visible hoy**: si hubiera préstamos `PENDIENTE_DESEMBOLSO` con el
    checklist todavía incompleto, la pantalla agregaría una nota — "No hay préstamos listos
    para desembolsar. Hay N préstamo(s) pendiente(s) esperando los documentos firmados, que se
    cargan en Formalización." (sin resultados) o "Además, hay N..." (con resultados) — hoy no
    aplica, no hay ningún préstamo en esa situación.
  - **Qué roles pueden entrar**: `FiltroAutorizacion` exige el permiso `DESEMBOLSAR` en la ruta
    `/desembolso` — `TOTAL` solo para CAJERO (y administrador, D7); gerente y oficial tienen
    `NINGUNO` (403 si fuerzan la URL, no recorrido hoy en pantalla).
- **Qué sigue**: "Desembolsar" sobre `CC-2026-NNNNN` → ficha de condiciones del desembolso
  (próximo paso).
- **Hallazgos**: ver la sección de Hallazgos al final de esta escena.

## Paso 2 — Ficha del desembolso: resumen, condiciones, comprobante y confirmación (RF16/RF17/RF18)

- **Usuario/rol**: Cajera.
- **Qué hace el usuario**: toca **"Desembolsar"** sobre `CC-2026-NNNNN` en la bandeja (Paso 1).
  Se abre `GET /desembolso/{id}`, título el **número de operación** (`CC-2026-NNNNN`).
- **Qué responde el sistema**: cuatro tarjetas, en este orden:
  1. **"Resumen del desembolso"**: tabla con **Cliente** (nombre y apellido — C.I. con puntos,
     Marta Ovelar — C.I. 1.000.001); **Monto aprobado** (número, y debajo, en gris chico, el
     monto en letras); **Deducciones** (**0 Gs.**, siempre); **Monto neto a entregar en
     efectivo** (igual al monto aprobado, en negrita); y, si hay caja abierta y de hoy, **Caja:
     saldo teórico antes** y **Caja: saldo teórico después** (saldo antes menos el monto
     aprobado — si quedara negativo, se muestra en rojo **"Faltan [monto]"** en vez del saldo).
  2. **"Condiciones para desembolsar"**: 4 ítems con una viñeta ✓ verde o una etiqueta roja cada
     uno — **Checklist de documentos firmados completo**; **Documentos generados vigentes
     (validez: 5 días desde la última emisión)**; **Caja abierta y lista para operar hoy**;
     **Saldo suficiente en caja** (si falta, agrega "— faltan [monto]"). Debajo, un aviso
     amarillo (no rojo) por cada condición que falte, con un enlace para resolverla ("Ir a
     formalización", "Ir a caja", o "Pida al gerente que entregue efectivo.") — hoy, las 4 están
     en verde, ningún aviso visible.
  3. **"Comprobante de desembolso"**: con las 4 condiciones en verde, el texto **"Imprima el
     comprobante, haga firmar al cliente y recién después confirme el desembolso."** y el botón
     **"Imprimir comprobante para firma"** (abre el PDF en una pestaña nueva). Si falta alguna
     condición, esta tarjeta no muestra el botón — en su lugar, **"No se puede imprimir
     todavía: complete las condiciones de la sección 'Condiciones para desembolsar' de
     arriba."**
  4. **"Confirmar desembolso"** (solo si las 4 condiciones están en verde): la casilla **"El
     cliente firmó el comprobante, verifiqué su cédula y le entregué [monto] en efectivo."**,
     deshabilitada, con la ayuda **"Imprima el comprobante (arriba) para habilitar esta
     casilla."**; el botón **"Confirmar desembolso"**, deshabilitado; y el enlace **"Volver a la
     bandeja"**.
- **Regla de negocio**: ver [RF16_condiciones_desembolso.md](../funcionamiento/RF16_condiciones_desembolso.md)
  (las 4 condiciones) y RF17 (comprobante antes de confirmar).
- **Qué sigue hoy en pantalla**: tocar "Imprimir comprobante para firma" habilita la casilla de
  "Confirmar desembolso" (sin recargar la página); tildarla habilita el botón "Confirmar
  desembolso". La confirmación en sí (movimientos de caja, saldo resultante, qué ve la cajera
  después) queda para el próximo paso.
- **La pantalla guía en orden y bloquea los pasos siguientes**: no se puede ver el botón de
  imprimir sin las 4 condiciones en verde, ni tildar la casilla sin haber impreso, ni confirmar
  sin tildar la casilla — cada tarjeta solo aparece o se habilita cuando la anterior ya se
  cumplió. **Buen ejemplo en pantalla real para la propuesta B10 de
  [04_formalizacion.md](04_formalizacion.md)** (reordenar la ficha de formalización por la tarea
  real del usuario, con un aviso "qué sigue" que cambia según el avance) — el desembolso ya hace
  exactamente ese tipo de guiado paso a paso.
- **Qué pasa por detrás, verificado en el código (solo lectura)**:
  - **Deducciones**: no sale de ningún cálculo ni tabla — `desembolso/ficha.jsp` escribe
    literalmente `FormateadorNumeros.comoGuaranies(BigDecimal.ZERO)`. Coherente con RN-08/RN-17
    (`cargos_operacion` sin usar, ya confirmado en tramos anteriores): el PDF del comprobante
    pone el mismo cero con la leyenda "(sin comisiones ni gastos)"
    (`ServicioDesembolso.generarPdfComprobante`, parámetro `MONTO_DEDUCCIONES`).
  - **Parámetro de validez (5 días)**: `formalizacion.dias_validez_documentos` (tipo ENTERO, hoy
    en 5) — lo lee `ControladorDesembolsoServlet.mostrarFicha` y, de nuevo, `ServicioDesembolso.
    asegurarCondicionesDesembolso` (la revalidación real, dentro de la transacción), ambos vía
    `ServicioParametros.obtenerValorEntero`. Se compara, en `ServicioDesembolso.
    validarDocumentosVigentes` (método puro), contra la `fecha_documento` de la versión VIGENTE
    del `CONTRATO_GENERADO` — no `fecha_formalizacion`, que nunca cambia — y "vigente" significa
    que hoy no pasó de `fecha_documento + 5 días`.
  - **Cómo sabe el sistema que se imprimió el comprobante, para habilitar la casilla**: **en los
    dos lados, con una diferencia importante**. En el navegador, el `onclick` del botón
    "Imprimir comprobante para firma" (`ficha.jsp`) le quita `disabled` a la casilla y cambia el
    texto de ayuda a "✓ Comprobante impreso" — sin esperar a que el PDF termine de generarse ni
    de abrirse, y sin recargar la página (quien edite el HTML con las herramientas del
    navegador podría destildar esta protección). El servidor, aparte, exige de forma
    independiente en `ServicioDesembolso.confirmarDesembolso` que
    `prestamo.getNumeroComprobanteDesembolso() != null` — si no, rechaza con **"Imprima el
    comprobante de desembolso (para firma) antes de confirmar."**, aunque la casilla del
    navegador hubiera quedado tildada a la fuerza. Ese número se asigna una sola vez, la primera
    vez que se pide la impresión (`generarComprobantePreDesembolso`, numerador `DESEMBOLSO`,
    formato `DES-AAAA-NNNNNN`), con el préstamo todavía `PENDIENTE_DESEMBOLSO`; las impresiones
    siguientes reutilizan el mismo número y quedan marcadas "REIMPRESIÓN" en el PDF. El botón de
    imprimir, a su vez, vuelve a exigir las 4 condiciones (`asegurarCondicionesDesembolso`) antes
    de generar el PDF — no es un atajo para saltárselas.
  - **La casilla en sí también se revalida en el servidor**: el valor real que viaja es un campo
    oculto (`confirmoEntregaEfectivo`) sincronizado por `onchange` con el checkbox visible;
    `confirmarDesembolso` rechaza con **"Debe verificar la cédula del cliente y confirmar que le
    entregó el efectivo."** si llega en `false` — no es solo cosmética del lado del cliente.
- **Hallazgos**: ver C11 más abajo.

## Nota: cierre de la sesión del día anterior y apertura de hoy

Entre el Paso 1 y el Paso 2, y antes de empezar el desembolso de hoy (22/10/2026), Joel, en el
rol de cajera, cerró manualmente la sesión de caja abierta anoche y abrió una nueva. Verificado
con `SELECT` en `sgcpc_db_pruebas` (solo lectura, nunca `sgcpc_db`) sobre `sesiones_caja`:

| Sesión | Estado | Apertura | Fondo inicial | Cierre | Saldo teórico (cierre) | Saldo físico | Diferencia | Motivo |
|---|---|---|---|---|---|---|---|---|
| N.º 1 | CERRADA | 21/10/2026 22:04:18 | 3.000.000 | 22/10/2026 06:09:33 | 3.000.000 | 3.000.000 | 0 | (ninguno — no se exige motivo cuando la diferencia es 0) |
| N.º 2 | ABIERTA | 22/10/2026 06:09:41 | 3.000.000 | — | — | — | — | — |

El cierre de la sesión 1 en sí (pantalla, confirmación, mensaje de éxito) se documentará con
detalle en el **tramo 6 (cierre del día)** — acá solo se registra el dato de base, porque ocurrió
entre dos pasos de este tramo y la sesión 2 es la que está abierta durante todo el resto de esta
escena.

**¿El sistema avisa o impide operar con una caja de un día anterior?** Verificado en el código
(solo lectura): `ServicioCaja.esDeHoy(SesionCaja)` compara la fecha de `fecha_hora_apertura` con
la fecha de hoy; si la sesión ABIERTA no es de hoy, `ServicioCaja.validarSesionDeHoy` lanza
`CajaDeOtroDiaException` con el mensaje exacto (`ServicioCaja.mensajeCajaDeOtroDia`): **"La caja
del [fecha] sigue abierta: ciérrela y rinda antes de operar hoy."** — usado por los 3
controladores que dependen de una caja de hoy (Caja, Cobranza y Desembolso, mismo texto en los
3). En nuestro caso esto no llegó a dispararse en pantalla: la cajera cerró la sesión 1 antes de
empezar a desembolsar, así que `ServicioDesembolso.asegurarCondicionesDesembolso` encontró la
sesión 2 (de hoy) ya abierta y nunca entró a esa rama.

### Confirmado en pantalla (23/10/2026, segundo cliente de práctica)

Esta vez la cajera tocó **"Desembolsar"** en `CC-2026-00002` con la caja del 22/10/2026 **todavía
abierta** (ver `00_apertura.md` § "Registro adicional: segunda pasada" — la misma sesión N.º 2
de la tabla de arriba, nunca cerrada después de ese desembolso). En `/desembolso/{id}`:

- **"Resumen del desembolso"**: Cliente, Monto aprobado, Deducciones (0), Monto neto
  (2.000.000) — **sin la línea de saldo de caja antes/después**, justo como anticipa el Paso 2
  ("si hay caja abierta y DE HOY" — hoy no lo es, así que esa línea no aparece).
- **"Condiciones para desembolsar"**: ✓ Checklist completo; ✓ Documentos vigentes; **"No
  disponible"** en *Caja abierta y lista para operar hoy*; **"Insuficiente"** en *Saldo
  suficiente en caja*. Debajo, el aviso amarillo **"La caja del 22/10/2026 sigue abierta:
  ciérrela y rinda antes de operar hoy."**, con el enlace **"Ir a caja"** — el mensaje exacto
  de `ServicioCaja.mensajeCajaDeOtroDia`, confirmado ahora en pantalla, no solo en el código.
- **"Comprobante de desembolso"**: **"No se puede imprimir todavía: complete las condiciones
  de la sección 'Condiciones para desembolsar' de arriba."**
- **No aparece la sección "Confirmar desembolso"** — solo **"Volver a la bandeja"**.

**Esto confirma en pantalla** el camino "desembolsar con una caja ABIERTA pero de un día
anterior" — pasa de "No recorrido" a **"Sí"** en la tabla de Completitud del tramo 4, más
abajo — y el mensaje exacto de `mensajeCajaDeOtroDia`, antes solo verificado en el código.

**Dato positivo para la fase 2**: el aviso de esta condición no solo informa el problema —
trae un enlace directo **"Ir a caja"**, al lugar exacto donde se resuelve. Es el patrón a
copiar para B32/B33 (`02_solicitud.md`: el camino real de 5 pasos de vuelta al Expediente,
sin ningún enlace directo desde el aviso de faltantes) — acá, en cambio, el aviso lleva
directo a la acción que lo resuelve, en un solo clic.

### Confirmado en pantalla, con la caja del día ya abierta (23/10/2026)

Con la sesión del 22/10 cerrada y la caja de hoy abierta (ver `07_cierre_del_dia.md` y
`00_apertura.md` § "Registro adicional"), la cajera volvió a `/desembolso/{id}` de
`CC-2026-00002`:

- **"Resumen del desembolso"**: Cliente, Monto aprobado (2.000.000), Deducciones (0), Monto
  neto (2.000.000) — **esta vez SÍ aparece** la línea de Caja: **saldo teórico antes
  2.000.000** / **después 0** (el monto aprobado completo, ya que la caja recién se abrió con
  ese mismo fondo y no tuvo ningún otro movimiento).
- **"Condiciones para desembolsar"**: las **4 en ✓** — checklist completo, documentos
  vigentes, caja abierta y lista para operar hoy, saldo suficiente en caja.
- **"Comprobante de desembolso"**: con las 4 condiciones en verde, el botón **"Imprimir
  comprobante para firma"** ya disponible.
- **"Confirmar desembolso"**: la casilla **"El cliente firmó el comprobante, verifiqué su
  cédula y le entregué 2.000.000 Gs. en efectivo."** y el botón **"Confirmar desembolso"**,
  ambos deshabilitados hasta imprimir el comprobante — igual que en la primera pasada (ver
  Paso 2 de esta escena, arriba).

**Contraste directo con el registro anterior**: la misma pantalla, para el mismo préstamo, en
el mismo día — con la única diferencia de qué caja estaba abierta al momento de entrar —
pasó de **2 condiciones en rojo y sin ninguna línea de saldo** a **las 4 en verde y con el
saldo antes/después visible**. Confirma, con el mismo ejemplo real, exactamente lo que ya
describía el Paso 2 sobre cuándo aparece esa línea ("si hay caja abierta y DE HOY").

## Paso 3 — Imprimir comprobante para firma: el PDF `comprobante_desembolso` (RF17)

- **Usuario/rol**: Cajera.
- **Qué hace el usuario**: con las 4 condiciones en verde, toca **"Imprimir comprobante para
  firma"** en la tarjeta "Comprobante de desembolso" (Paso 2). Se abre, en una pestaña nueva, el
  PDF del comprobante de desembolso — dos páginas, misma plantilla en ambas: página 1
  **"ORIGINAL – Empresa"**, página 2 **"COPIA – Cliente"**.
- **Qué responde el sistema (contenido del PDF, verificado en la plantilla)**: en cada una de las
  2 páginas:
  - **Encabezado**: logo, razón social y RUC de la empresa, domicilio, teléfono y WhatsApp; a la
    derecha, la etiqueta de ejemplar ("ORIGINAL – Empresa" o "COPIA – Cliente"); franja de color
    institucional.
  - **Título**: **"COMPROBANTE DE DESEMBOLSO"**, y debajo, en cursiva chica, **"Documento interno
    – no válido como comprobante fiscal"**.
  - **N.º del comprobante**: "N.º DES-2026-NNNNNN" (hoy, el primero del año — formato de 6
    dígitos, ver B21 abajo). Si fuera una reimpresión, acá aparecería además, en rojo, "Reimpreso
    por [usuario] el [fecha/hora]." — hoy no aplica, es la primera impresión.
  - **Lugar/fecha/hora y operación**: "Asunción, [fecha y hora]" y "Operación: CC-2026-NNNNN".
  - **Caja y cajera**: "Caja: Sesión N.º 2 (abierta 22/10/2026)" y "Cajera: [nombre de quien
    imprime]".
  - **Datos del cliente**: "Apellidos y nombres: Marta Ovelar C.I.: 1.000.001" y "Domicilio:
    [domicilio del cliente]" (en minúscula — ya es B13, no se repite acá).
  - **Detalle de la operación**: "Préstamo N.º CC-2026-NNNNN — Contrato: [n.º] — Pagaré: [n.º]";
    fila "Monto aprobado:" con el monto; fila "Deducciones:" con **0 Gs.**; fila resaltada
    **"MONTO NETO ENTREGADO:"** con el monto neto, y debajo, en cursiva chica, el mismo monto en
    letras; "Forma de entrega: Efectivo" y "Plazo: [N] cuotas mensuales"; "Cuota (con IVA): [monto
    de la 1.ª cuota]" (si el plan es decreciente, agrega "la primera, decreciente hasta [monto]
    (ver plan de pagos)"); "Primer vencimiento: [fecha]" (ver C12 abajo); "TEA: [%]" y "CFT: [%]".
  - **Leyenda de mora**: "En caso de atraso se aplican interés moratorio (a la tasa pactada) e
    interés punitorio (30% del moratorio), más IVA."
  - **"Recibí conforme..."**: "Recibí conforme la suma de [monto en letras] ([monto número]) en
    efectivo, en concepto de desembolso del préstamo N.º CC-2026-NNNNN, según contrato y pagaré
    suscritos."
  - **Firmas**: dos líneas, "Firma y aclaración del cliente" (con "C.I.: 1.000.001" debajo) y
    "Firma y aclaración de la cajera" (con el nombre de la cajera debajo).
  - **Pie**: "Emitido por: [cajera] — [fecha/hora de emisión]" y, a la derecha, "Página X de 2".
- **Al volver a la pantalla** (sin recargar, misma pestaña de la ficha): aparece **"✓ Comprobante
  N.º DES-2026-NNNNNN ya impreso"** junto al botón, y se habilita la casilla de "Confirmar
  desembolso" (ver Paso 2 — mecanismo cliente + servidor).
- **Regla de negocio**: RF17 (comprobante antes de confirmar), ver también el Paso 2.
- **Qué pasa por detrás, verificado en el código (solo lectura)**:
  - **Plantilla**: `reportes/comprobante_desembolso.jrxml` — sin ningún cálculo propio (todos los
    parámetros llegan ya formateados desde `ServicioDesembolso.generarPdfComprobante`), dos
    bandas (`title` = ORIGINAL, `summary` = COPIA) con el mismo contenido, `isSummaryNewPage`
    fuerza el salto de página entre una y otra.
  - **Numerador**: `NumeradorDAO.siguienteValor(conexion, "DESEMBOLSO", año)`, formateado como
    `String.format("DES-%d-%06d", año, secuencia)` en `ServicioDesembolso.
    generarComprobantePreDesembolso` — correlativo por año, independiente del numerador `CC` de
    la operación.
  - **¿Se guarda el PDF?** No — se genera al vuelo en cada pedido (`JasperFillManager.
    fillReport`/`JasperExportManager.exportReportToPdf`, dentro de `generarPdfComprobante`) y se
    devuelve directo en la respuesta HTTP; no se escribe a disco ni se cachea ninguna versión
    vieja. Lo único que persiste es el N.º de comprobante (`prestamos.
    numero_comprobante_desembolso`, asignado una sola vez) y el registro de auditoría.
  - **¿Queda auditoría de la impresión?** Sí — `ServicioDesembolso.registrarImpresionComprobante`
    registra `IMPRIMIR_COMPROBANTE_DESEMBOLSO` (primera vez) o `REIMPRIMIR_COMPROBANTE_DESEMBOLSO`
    (reimpresiones), entidad `PRESTAMO`, con el detalle "Imprimió"/"Reimprimió el comprobante de
    desembolso."
- **Hallazgos**: ver C12 y B21 más abajo.

### Registro adicional: segunda pasada (23/10/2026, segundo cliente de práctica)

**"Imprimir comprobante para firma"** de `CC-2026-00002` → PDF `comprobante_desembolso_2` (2
páginas, "ORIGINAL – Empresa" y "COPIA – Cliente"): **N.º DES-2026-000002** (correlativo);
**"Asunción, 23/10/2026 10:31"**; **"Caja: Sesión N.º 3 (abierta 23/10/2026)"**; cajera; datos
del cliente; detalle (préstamo/contrato/pagaré `CC-2026-00002`, monto aprobado 2.000.000,
deducciones 0, monto neto entregado en número y letras, forma de entrega efectivo, plazo 12,
cuota con IVA 193.280 — la primera, decreciente hasta 189.629 —, **"Primer vencimiento:
23/11/2026"**, TEA 27 %, CFT 30,0384 %); leyenda de mora; "Recibí conforme..."; firmas del
cliente (con C.I.) y de la cajera; pie "Emitido por" y "Página X de 2". Igual estructura que
la primera pasada (Paso 3, arriba).

Ver C12 (actualizado) y B13 (confirmación) en Hallazgos.

## Paso 4 — Confirmar desembolso: movimientos de caja, saldo resultante y qué ve la cajera después (RF18)

- **Usuario/rol**: Cajera.
- **Qué hace el usuario**: tilda la casilla **"El cliente firmó el comprobante, verifiqué su
  cédula y le entregué [monto] en efectivo."** (habilitada desde el Paso 3) y toca **"Confirmar
  desembolso"**.
- **Confirmación**: SweetAlert2 — **"¿Confirmar el desembolso de [monto]?"**, botones **"Sí,
  desembolsar"** / **"Cancelar"**.
- **Éxito**: modal verde — título **"Desembolso de CC-2026-NNNNN confirmado ([monto])"**, con un
  botón **"Imprimir comprobante"** (abre el PDF en una pestaña nueva) y **"Aceptar"**. El sistema
  vuelve a `/desembolso`, que ahora muestra **"No hay préstamos listos para desembolsar."** (era
  el único préstamo de la bandeja).
- **Regla de negocio**: RF18 (movimientos de caja del desembolso), continúa el Paso 2/RF16-RF17.
- **Qué pasa por detrás, verificado en el código y con `SELECT` en `sgcpc_db_pruebas` (solo
  lectura, nunca `sgcpc_db`)**:
  - **Método**: `POST /desembolso/{id}/confirmar` → `ControladorDesembolsoServlet.
    procesarConfirmar` → `ServicioDesembolso.confirmarDesembolso`, una sola transacción.
  - **Estado del préstamo, antes y después** (verificado con `SELECT` sobre `prestamos`):
    `PENDIENTE_DESEMBOLSO` ("Pendiente de desembolso") → **`ACTIVO`** ("Activo"); se asigna
    `fecha_desembolso = 22/10/2026` (la fecha real de la confirmación, `Reloj.hoy()`, no la fecha
    de formalización). El `numero_comprobante_desembolso` **no cambia** — sigue siendo el mismo
    `DES-2026-000001` que ya se había asignado al imprimir en el Paso 3; `confirmarDesembolso` lo
    reutiliza, nunca genera uno nuevo (y de hecho rechaza si viniera nulo, ver Paso 2).
  - **Estado de la solicitud**: **no cambia** — sigue en `APROBADA` ("Aprobada"), verificado con
    `SELECT` sobre `solicitudes_credito`. `confirmarDesembolso` no toca esa tabla; `APROBADA`
    sigue siendo el estado final de la solicitud durante toda la formalización y el desembolso.
  - **Movimiento de caja creado** (verificado con `SELECT` sobre `movimientos_caja`): tipo
    **`DESEMBOLSO`**, monto **3.000.000 Gs.**, signo **−1**, `referencia_tipo = DESEMBOLSO` (con
    `id_referencia` apuntando a la fila nueva de `desembolsos`), `registrado_por` = la cajera,
    observación "Desembolso de CC-2026-00001." La fila de `desembolsos` en sí guarda el mismo
    monto (`monto_aprobado_snapshot`/`monto_neto_entregado` = 3.000.000), el mismo
    `numero_comprobante` (`DES-2026-000001`) y el `identificador_confirmacion` (UUID) usado para
    la idempotencia.
  - **Saldo teórico resultante**: la sesión de caja N.º 2 (ver la Nota, antes del Paso 3) abrió
    con fondo 3.000.000 y este es su único movimiento — saldo teórico después del desembolso:
    **3.000.000 − 3.000.000 = 0 Gs.** (verificado sumando `sesiones_caja.saldo_inicial` +
    `movimientos_caja` de la sesión 2 con `SELECT`).
  - **¿La fecha de desembolso cambia algo del plan?** No las fechas ni los montos de las cuotas
    — esos ya quedaron fijos al formalizar (plan vigente, `primera_fecha_vencimiento`, sin
    tocarse acá). `fecha_desembolso` sí se usa en otro lugar, más adelante: en el motor
    financiero (`ServicioMotorFinanciero.inicioPeriodoTeorico`), es la fecha de referencia para
    calcular el inicio del período de interés de la **1.ª cuota** (solo cuando el préstamo no
    viene de un anticipo parcial) — relevante para la cobranza futura de este préstamo, no para
    hoy (recién desembolsado, sin cuotas vencidas todavía).
  - **Auditoría** (verificado con `SELECT` sobre `auditoria_operaciones`, préstamo CC-2026-00001;
    nombres legibles de `EtiquetasAuditoria.comoEtiquetaLegible`, que muestra el código crudo sin
    traducir si no hay una entrada mapeada):

    | Hora | Operación | Nombre legible | Detalle |
    |---|---|---|---|
    | 06:10:16 | `IMPRIMIR_COMPROBANTE_DESEMBOLSO` | *(sin mapear — se vería el código crudo)* | "Imprimió el comprobante de desembolso." |
    | 06:20:26 | `DESEMBOLSAR` | **"Desembolsar préstamo"** | "Desembolsó 3.000.000 Gs. de CC-2026-00001." |
    | 06:20:36 | `REIMPRIMIR_COMPROBANTE_DESEMBOLSO` | *(sin mapear — se vería el código crudo)* | "Reimprimió el comprobante de desembolso." |

    El tercer evento es el clic en "Imprimir comprobante" del modal de éxito: aunque es la
    primera vez que se ve el comprobante *después* de confirmado, el sistema lo registra igual
    como reimpresión — la impresión "para firma" del Paso 3 ya consumió el único cupo de
    "impresión original" (`ControladorDesembolsoServlet`, `doGet` de `/comprobante`: decide
    `generarComprobantePreDesembolso` vs. `obtenerPdfComprobanteDesembolso` únicamente según si
    el préstamo sigue `PENDIENTE_DESEMBOLSO`; una vez `ACTIVO`, siempre es reimpresión).
  - **¿Qué pasa si se intenta confirmar dos veces (doble clic o volver atrás)?** El campo oculto
    `identificadorConfirmacion` (un UUID generado una sola vez, al cargar la ficha con `GET`)
    viaja igual en los dos envíos. `confirmarDesembolso` lo busca primero en `desembolsos`: si ya
    existe una fila con esa misma clave, no inserta nada de nuevo (ni desembolso ni movimiento de
    caja) y hace `commit` igual, devolviendo el mismo resultado — cubre tanto el doble clic como
    volver atrás con el mismo formulario y reenviarlo. Si una carrera real hiciera que los dos
    clics pasaran ese chequeo antes de que el primero comiteara, la restricción `UNIQUE` de
    `identificador_confirmacion` frena al segundo en el motor de la base (`SQLState 23505`), y
    ese error también se trata como éxito idempotente, no como fallo. Si en cambio la cajera
    recarga la página del préstamo (F5) después de confirmar, el `GET` ya encuentra el préstamo
    `ACTIVO` y muestra la tarjeta "Desembolsado el..." en vez de las condiciones o el formulario
    — no queda ninguna forma de volver a ver el formulario de confirmación para ese préstamo.
- **Hallazgos**: ver B22 más abajo.

### Registro adicional: segunda pasada (23/10/2026, segundo cliente de práctica)

Con la casilla marcada (**"✓ Comprobante impreso"**), **"Confirmar desembolso"** →
confirmación **"¿Confirmar el desembolso de 2.000.000 Gs.?"** (**"Sí, desembolsar"**/
**"Cancelar"**) → éxito **"Desembolso de CC-2026-00002 confirmado (2.000.000 Gs.)"** con
**"Imprimir comprobante"** y **"Aceptar"** → `/desembolso` vuelve a mostrar **"No hay préstamos
listos para desembolsar."** Igual que en la primera pasada; **B22 se repite** (el mensaje de
éxito no dice qué sigue).

**Verificado con `SELECT` en `sgcpc_db_pruebas`**:

- **Préstamo**: `CC-2026-00002` → `estado_prestamo = 'ACTIVO'`, `fecha_desembolso =
  23/10/2026`.
- **Movimiento de caja**: `DESEMBOLSO`, monto 2.000.000, signo −1, en la **sesión N.º 3**
  (23/10/2026 10:35:55) — saldo teórico resultante: 2.000.000 (fondo inicial de la sesión 3) +
  (−2.000.000) = **0 Gs.**
- **Numerador DES**: `DES-2026-000002`, `estado_desembolso = 'CONFIRMADO'` — correlativo real,
  sin huecos (`000001` → `000002`), igual que el numerador `CC`.
- **Auditoría**: `IMPRIMIR_COMPROBANTE_DESEMBOLSO` (23/10/2026 10:31:39, "Imprimió el
  comprobante de desembolso.") seguida de `DESEMBOLSAR` (23/10/2026 10:35:55, "Desembolsó
  2.000.000 Gs. de CC-2026-00002.") — mismo patrón de 2 eventos que el primer cliente.

## Paso 5 — Revisar la caja después del desembolso: pantalla /caja (RF18)

- **Usuario/rol**: Cajera.
- **Qué hace el usuario**: abre **"Desembolso y caja → Caja"**. Se abre `GET /caja`.
- **Qué responde el sistema**: con la sesión N.º 2 todavía abierta, tres tarjetas:
  1. **"Sesión abierta"**: tabla con **Apertura** (22/10/2026 06:09:41), **Fondo inicial**
     (3.000.000 Gs.) y **Saldo teórico actual** (**0 Gs.**, en negrita).
  2. **"Movimientos"**: tabla con columnas **Fecha/hora, Tipo, Monto, Observación, Comprobante**
     y una columna sin encabezado para el botón de reimpresión. Dos filas: la primera, fija (no
     es una fila real de `movimientos_caja`), **"Apertura (fondo inicial)"**, `+3.000.000 Gs.`,
     sin observación, comprobante "-"; la segunda, el movimiento real, **"Desembolso"**,
     `-3.000.000 Gs.`, observación **"Desembolso de CC-2026-00001."**, comprobante
     **"DES-2026-000001"** y, al final, el botón **"Reimprimir"** (la cajera tiene permiso para
     verlo).
  3. **"Cierre y rendición"**: campo **"Efectivo contado"**, campo **"Motivo de la diferencia
     (obligatorio si el efectivo contado no coincide con el saldo teórico)"**, un texto que
     compara en vivo lo contado contra el saldo teórico (hoy, 0 Gs.) y el botón **"Cerrar
     caja"** — visible, sin tocarse hoy (el cierre de esta sesión N.º 2 queda para el tramo 6).
  - (La tarjeta **"Entregar efectivo a la caja"** no aparece: es solo para gerente/administrador,
    y hoy la cajera está logueada. La tarjeta **"Sesiones cerradas"**, al final de la pantalla,
    ya muestra la sesión N.º 1 de anoche — Apertura 21/10/2026 22:04, Cierre 22/10/2026 06:09,
    diferencia **0 Gs.**, sin "(revisada)" porque una diferencia de 0 no entra en la lista de
    "sin revisar" — ver la Nota, antes del Paso 3.)
- **Regla de negocio**: RF18 (movimientos de caja), continúa el Paso 4.
- **Qué pasa por detrás, verificado en el código (solo lectura)**:
  - **Método**: `ControladorCajaServlet.mostrarFicha` → `ServicioCaja.obtenerSesionAbierta` +
    `calcularSaldoTeorico` + `listarMovimientosConComprobante`.
  - **Fila "Desembolso" de la tabla**: `ServicioCaja.listarMovimientosConComprobante` detecta
    `referencia_tipo = 'DESEMBOLSO'` y busca el `Desembolso` real (`DesembolsoDAO.obtenerPorId`)
    para completar **comprobante** (`desembolso.getNumeroComprobante()`) y la URL del botón
    "Reimprimir" (`/desembolso/{idPrestamo}/comprobante` — la misma ruta del Paso 3, que decide
    sola si es impresión original o reimpresión según el estado del préstamo). El texto del
    "Tipo" ("Desembolso") sale de `TipoMovimientoCaja.DESEMBOLSO.getEtiqueta()` — mismo mecanismo
    que "Entrega del gerente"/"Rendición al gerente" para los otros tipos.
  - **"Saldo teórico actual"**: `ServicioCaja.calcularSaldoTeorico` = `saldo_inicial +
    SUM(movimientos.monto × signo)` — la misma fórmula ya confirmada en C10 y en el Paso 4;
    verificado con `SELECT` (sesión 2: 3.000.000 + (−3.000.000) = **0**).
  - **Botón "Reimprimir"**: solo se muestra si `Permiso.VER_COMPROBANTES_COBRANZA` no es
    `NINGUNO` para el perfil — la cajera tiene `CONSULTA` (igual que el gerente), así que lo ve;
    el oficial tiene `NINGUNO`. Es defensa en profundidad: la ruta real
    (`/desembolso/{id}/comprobante`) ya está protegida aparte por `FiltroAutorizacion`
    (`DESEMBOLSAR`, solo cajero/administrador) — un oficial no podría usarla aunque el botón se
    mostrara.
- **Hallazgos**: ver B23 y B24 más abajo.

## Pasos previstos

### Paso 6 — Entrega de efectivo adicional del gerente a la caja (RF18)

## Hallazgos de esta escena (tramo 4, inicio)

Separados de "Cómo funciona hoy" (arriba, ficha por paso).

- **B20** — La pantalla no muestra el efectivo disponible en caja — la cajera ve qué préstamos
  están listos y su monto, pero tiene que ir a `/caja` para saber si el efectivo alcanza.
  Sugerir mostrar **"Efectivo en caja: [monto]"** arriba de la lista (mismo dato que ya calcula
  `ServicioCaja.calcularSaldoTeorico`, reutilizado sin ningún cálculo nuevo).
- **B21** — El numerador del comprobante de desembolso usa 6 dígitos (`DES-2026-NNNNNN`,
  `String.format("DES-%d-%06d", ...)`), mientras que el número de operación del préstamo usa 5
  (`CC-2026-NNNNN`, `String.format("CC-%d-%05d", ...)`, `ServicioFormalizacion` línea 863).
  Sugerir unificar el formato (mismo largo de dígitos) para que no parezca una inconsistencia al
  mirar los dos números uno al lado del otro en el mismo comprobante.
  (El domicilio del cliente en minúscula en el PDF ya está anotado como **B13**, no se repite
  acá.)
- **B13 (confirmación, 23/10/2026)** — El comprobante de desembolso de `CC-2026-00002` también
  imprime el domicilio con la coma duplicada ya documentada: **"Barcequillo,,"** (mismo barrio
  cargado con una coma al final, ya confirmado en la autorización de buró de este mismo
  cliente, ver `01_llega_el_cliente.md`). Coherente con la causa raíz ya identificada:
  `ServicioDesembolso.generarPdfComprobante` llama al mismo
  `ServicioClientes.obtenerTextoDomicilio` que usan todos los demás documentos, armado en
  `ServicioClientes.construirTextoDomicilio` con `String.join(", ", partes)` sin limpiar la
  puntuación sobrante de la parte anterior — no es un problema puntual de este PDF, es el mismo
  método compartido fallando de nuevo en otro documento más.
- **B22** — El mensaje de éxito del desembolso ("Desembolso de CC-2026-NNNNN confirmado
  [monto]") no indica qué sigue — la cajera no ve, en ese mismo modal, si quedó efectivo en caja,
  en qué estado quedó el préstamo, ni cuándo vence la primera cuota. Sugerir agregar al modal (o
  a la bandeja a la que vuelve) el **saldo en caja resultante** (ya calculado,
  `MovimientoCajaDAO.sumarMovimientos`/`ServicioCaja.calcularSaldoTeorico`), el **estado del
  préstamo** ("Activo") y el **primer vencimiento efectivo** (la fecha real, no la elegida — ver
  C12) para que la cajera pueda informarle algo concreto al cliente antes de que se vaya.
- **B23 — RESUELTO el 25/10/2026** (commit `99a77b3`; fila cerrada el 01/11/2026). Hoy
  `EtiquetasAuditoria:79-80` mapea las dos y `auditoria.jsp` las muestra en español. Dueño del
  estado: `FASE2_PLAN.md` y `auditoria/AUDITORIA_TECNICA.md`. Lo que decía el hallazgo:
  `IMPRIMIR_COMPROBANTE_DESEMBOLSO` y `REIMPRIMIR_COMPROBANTE_DESEMBOLSO` no tenían
  nombre legible en `EtiquetasAuditoria` — en la pantalla de auditoría (RF40) se verían con el
  código técnico en vez de un texto en español, a diferencia de prácticamente cualquier otra
  operación del sistema. Quedaron fuera de la tanda de nombres legibles del punto 8 (que sí
  cubrió `ABRIR_CAJA`/`CERRAR_CAJA`/`ENTREGAR_EFECTIVO_CAJA`/`REVISAR_DIFERENCIA_CAJA`/
  `DESEMBOLSAR`, todas las demás operaciones de este mismo módulo).
  **Verificado en todo el código (no solo este módulo) si hay otras operaciones registradas sin
  etiqueta**: revisé los 17 archivos que llaman a `ServicioAuditoria.registrar`/
  `registrarCambioDeValor`/`registrarIntentoUsuarioInexistente` (`FiltroAutorizacion`,
  `ListenerSesion`, y 15 servicios) y comparé cada código de operación realmente usado (literal o
  por variable, ej. `ServicioUsuarios.cambiarEstadoConGuarda` con `BLOQUEAR_USUARIO`/
  `INACTIVAR_USUARIO`, `ServicioDocumentos.cargarDocumento*` con `CARGAR_DOCUMENTO(_FORMALIZACION)`/
  `NUEVA_VERSION_DOCUMENTO(_FORMALIZACION)`) contra el mapa de `EtiquetasAuditoria`. **Resultado:
  `IMPRIMIR_COMPROBANTE_DESEMBOLSO` y `REIMPRIMIR_COMPROBANTE_DESEMBOLSO` (las dos, de
  `ServicioDesembolso.registrarImpresionComprobante`) son las ÚNICAS dos operaciones de todo el
  sistema sin nombre legible mapeado** — ningún otro módulo quedó afuera de esa tanda.
- **B24** — La tabla "Movimientos" de `/caja` no muestra el saldo acumulado después de cada
  movimiento — para saber el saldo en cualquier punto intermedio del día (con varios
  movimientos) habría que sumar a mano. Sugerir una columna **"Saldo"** con el acumulado fila por
  fila (mismo dato que ya calcula `ServicioCaja.calcularSaldoTeorico`, aplicado progresivamente
  en vez de una sola vez al final).

## C10 — ¿El sistema impide desembolsar sin caja abierta o con efectivo insuficiente?

Verificado en el código (solo lectura, sin ejecutar nada — el desembolso en sí queda para el
próximo paso, no recorrido todavía en pantalla):

- **Sin caja abierta**: `ServicioDesembolso.asegurarCondicionesDesembolso` (llamado desde
  `confirmarDesembolso`, el `POST` real) revisa `sesionCajaDAO.obtenerAbierta` — si no hay
  ninguna, rechaza con **"No hay una caja abierta."** (`NegocioException`), antes de escribir
  nada. También valida `servicioCaja.validarSesionDeHoy` (si la sesión abierta es de un día
  anterior, rechaza igual, con el mismo mensaje que ya se vio en Caja/Cobranza).
- **Efectivo insuficiente**: el mismo método calcula el saldo teórico
  (`sesion.getSaldoInicial() + sumarMovimientos`) y lo compara contra el monto aprobado; si no
  alcanza, rechaza con el mensaje exacto de
  `ServicioDesembolso.construirMensajeSaldoInsuficiente`: **"Hay [saldo teórico] en caja y el
  desembolso es de [monto]. Faltan [faltante]. Pedí al gerente que entregue efectivo."**
- **También revisa, en el mismo método**: checklist completo ("Faltan documentos firmados;
  complete el checklist antes de desembolsar.") y vigencia de los documentos generados ("Los
  documentos generados vencieron... regenérelos antes de desembolsar.") — ninguno de los 4
  motivos de rechazo escribe nada hasta que los 4 pasan juntos.
- **No recorrido todavía en pantalla**: ninguno de estos 4 rechazos se forzó hoy (la caja está
  abierta, con fondo suficiente para el único préstamo listo, checklist completo y documentos
  recién generados) — queda para una prueba futura, forzando cada condición por separado
  (ej. cerrar la caja antes de desembolsar, o abrir con un fondo menor al monto a desembolsar).

## C11 — Documentos vencidos con firmados ya cargados: ¿hay algún camino de salida?

**La validez es de 5 días desde la última emisión (`formalizacion.dias_validez_documentos`,
ver Paso 2), pero una vez que hay un firmado cargado, el sistema ya no deja volver a emitir el
documento generado que le da una `fecha_documento` nueva.** Verificado en el código (solo
lectura, sin forzar nada en pantalla — hoy el contrato de Marta Ovelar se generó recién, no está
vencido):

- **Qué ve la cajera si se vence**: en `/desembolso/{id}`, la tarjeta "Condiciones para
  desembolsar" marca "Documentos generados vigentes" en rojo ("Vencido") y aparece el aviso
  amarillo **"Los documentos generados están vencidos o no existen. Regénerelos antes de
  desembolsar."**, con el enlace **"Ir a formalización"** — "Comprobante de desembolso" y
  "Confirmar desembolso" dejan de mostrarse (no se puede imprimir ni confirmar). El mismo
  rechazo existe en el servidor (`ServicioDesembolso.asegurarCondicionesDesembolso`): **"Los
  documentos generados vencieron (más de 5 días sin desembolsar); regenérelos antes de
  desembolsar."**
- **A dónde lleva "Ir a formalización"**: a `/formalizacion/{id}`, a las tarjetas "Re-emitir los
  PDF (sin cambiar plan)" y "Regenerar documentos" — pero **ambas revisan, en el servidor
  (`ServicioFormalizacion.asegurarSinDocumentosFirmados`, llamada desde `regenerarSoloPdfs` y
  `regenerarDocumentos`), si ya hay un `CONTRATO_FIRMADO`, `PAGARE_FIRMADO` o
  `PLAN_PAGOS_FIRMADO` vigente cargado — y si hay alguno, las dos rechazan con el mismo mensaje:
  "No se puede re-emitir: ya hay documentos firmados cargados." / "No se puede regenerar: ya hay
  documentos firmados cargados."** La ficha de formalización ya oculta los botones y muestra
  ese mismo texto en gris en vez del formulario (`formalizacion/ficha.jsp`, variable
  `hayFirmadoCargado`) — pantalla y servidor dicen lo mismo, no es solo un candado del lado del
  cliente.
- **Dicho de otra forma**: el checklist de firmados (RF13, lo que habilita el desembolso) y la
  vigencia de los documentos generados (RF16, lo que la vuelve a bloquear a los 5 días) son dos
  controles independientes sobre la misma operación, y cada uno le cierra la única salida del
  otro — el checklist completo es justamente la condición que impide regenerar el documento
  generado que destrabaría la vigencia.
- **¿Existe algún camino de salida, y para quién?** Encontré uno solo, y no es una renovación de
  esta operación: **`ServicioFormalizacion.anularAntesDeDesembolso`** (gerente o administrador,
  con motivo obligatorio) cierra el préstamo como `ANULADO_ANTES_DESEMBOLSO` — nunca se
  desembolsa con este número de operación; para el cliente, esto implica volver a empezar una
  solicitud nueva desde cero (RF01), con un contrato y un pagaré nuevos para firmar. No encontré
  ningún método que renueve, extienda o vuelva a emitir el documento generado de una operación
  con firmados ya cargados.
  - Hay, además, una vía indirecta y mucho más general: el parámetro
    `formalizacion.dias_validez_documentos` se edita en `/parametros`
    (`Permiso.ADMINISTRAR_PARAMETROS` — `TOTAL` solo para administrador; el gerente tiene
    `CONSULTA`, solo puede ver, no editar). Como `ServicioDesembolso.validarDocumentosVigentes`
    compara contra el valor **actual** del parámetro (no el que estaba vigente el día de la
    emisión), subir este número "revalida" al instante cualquier documento ya vencido de
    **cualquier préstamo del sistema**, no solo el de este caso — es un camino de salida real,
    pero no pensado para un caso puntual: cambia la regla para todos, de forma retroactiva, hasta
    que alguien la vuelva a bajar.
- **Si no hay salida puntual, ¿es un camino sin salida?** Para esta operación en particular, sí:
  con firmados cargados y documentos vencidos, no hay ninguna acción dentro de los flujos de
  Formalización o Desembolso que vuelva a poner "Documentos generados vigentes" en verde sin
  anular el préstamo entero. **No decido acá si esto es A (no cumple el anteproyecto) o B (mejora
  de uso, el RF no lo exige) — RF16 exige la condición de vigencia, pero el anteproyecto no dice
  qué pasa después de que se vence con firmados ya cargados; queda para que Joel o el dueño de
  Capital Crédito lo decida.**

## C12 — El comprobante muestra la fecha elegida de primer vencimiento, no la fecha efectiva (probable A, misma familia que C3)

**29/10/2026 — lo que vi: corregido en pantalla.** Con `CC-2026-00005` (elegida sábado 28/11/2026),
el comprobante **`DES-2026-000004`** mostró *"Primer vencimiento: **30/11/2026**"*, la misma fecha que
el contrato y el plan; el desembolso se confirmó (3.000.000, caja de Sofía) y el préstamo quedó
`ACTIVO`. Dos cosas más que vi ese día, verificadas después en el código y anotadas en
`RF17_comprobante_desembolso.md` §7b: el comprobante **no se guarda** como PDF (se regenera en cada
impresión, solo se audita la impresión), y la hora que muestra es la de la **operación** — la
impresión "para firma" usa la hora de ese momento porque todavía no hay desembolso confirmado, y la
reimpresión usa la hora en que la cajera confirmó. Estado del ítem en
[FASE2_PLAN.md](FASE2_PLAN.md).

El comprobante de desembolso de Marta Ovelar muestra **"Primer vencimiento: 21/11/2026"** (la
fecha que se eligió al formalizar), pero la fecha efectiva — la que realmente corre el plan de
pagos cuando el día elegido cae en fin de semana o feriado — es el **lunes 23/11/2026**, igual
que ya muestran la ficha del préstamo, el contrato y el plan de pagos (confirmado en tramos
anteriores de este recorrido).

**Ubicado en el código de dónde toma la fecha la plantilla (sin corregir, solo lectura)**: el
parámetro `PRIMER_VENCIMIENTO` del `.jrxml` sale de
`ServicioDesembolso.generarPdfComprobante`:

```java
parametros.put("PRIMER_VENCIMIENTO", FormateadorFechas.comoDdMmAaaa(prestamo.getPrimeraFechaVencimiento()));
```

`prestamo.getPrimeraFechaVencimiento()` es la fecha **elegida** (la que quedó guardada en
`prestamos.primera_fecha_vencimiento` al formalizar), no la fecha habil/efectiva que calcula
`ServicioMotorFinanciero.fechaHabil` contra el calendario de feriados — esa fecha efectiva es la
que de verdad usa el motor financiero para generar la primera cuota del plan. El mismo campo
(`prestamo.getPrimeraFechaVencimiento()`) alimenta, sin distinguir elegida de efectiva, tanto
este comprobante como (ya visto en tramos anteriores) la ficha y el contrato — es la misma
familia de causa que C3.

**Probable A**: si el anteproyecto exige que los documentos reflejen la fecha real de la
primera cuota (a confirmar leyendo el RF correspondiente), mostrar la fecha elegida en vez de la
efectiva en un documento que el cliente firma sería una discrepancia entre lo firmado y lo que
realmente se cobra. No decido la clasificación final — queda para que Joel o el dueño de
Capital Crédito la confirme contra el RF exacto.

**Aclaración con el segundo cliente de práctica (23/10/2026), igual que ya se hizo con C3**:
el comprobante de `CC-2026-00002` muestra **"Primer vencimiento: 23/11/2026"** — y esta vez
**coincide** con el plan real (la primera cuota también vence el 23/11/2026, confirmado con
`SELECT` en `04_formalizacion.md`). No es que el hallazgo se haya corregido: para este cliente,
la fecha **elegida** al formalizar (23/11/2026) ya era un día hábil (lunes), así que coincide
con la fecha **efectiva** sin que haga falta ningún corrimiento. **C12 solo se nota cuando la
fecha elegida cae en un día no hábil y el sistema la corre** (como pasó con el primer cliente,
21/11/2026 sábado → 23/11/2026) — con el segundo cliente, el mismo código
(`prestamo.getPrimeraFechaVencimiento()`, sin distinguir elegida de efectiva) sigue sin
distinguir las dos fechas, simplemente no hay diferencia que mostrar esta vez. Mismo contraste
ya documentado para C3 en `04_formalizacion.md`.

## Segunda pasada de desembolso (23/10/2026)

Resumen en orden real de todo el circuito de la cajera con el **segundo cliente de práctica**
(`CC-2026-00002`), de punta a punta — cada paso enlaza al "Registro adicional" ya documentado
arriba o en las escenas vecinas, con sus hallazgos, sin repetir el contenido completo acá.

1. **Panel de la cajera**: avisa el trabajo pendiente ("1 préstamo pendiente de desembolso · 1
   listo para desembolsar"), pero **no avisa** que la caja abierta es de un día anterior.
   [00_apertura.md](00_apertura.md) § "Registro adicional: segunda pasada". **B49**.
2. **Bandeja `/desembolso`**: entra con `CC-2026-00002` listo. [05_desembolso.md](05_desembolso.md)
   Paso 1.
3. **Intento bloqueado por la caja de otro día**: "Condiciones para desembolsar" con 2
   condiciones en rojo, aviso exacto de `mensajeCajaDeOtroDia`, sin sección de confirmar.
   "Registro adicional" del Paso 2 (arriba).
4. **"Ir a caja"**: enlace directo desde el propio aviso — dato positivo para la fase 2
   (modelo para B32/B33).
5. **Cierre de la sesión del 22/10** (sin diferencia): confirmación y éxito resumiendo el
   monto. [07_cierre_del_dia.md](07_cierre_del_dia.md) Paso 1. **B51**.
6. **Arqueo**: PDF de la sesión N.º 2, con la firma "Recibí" del gerente predeterminado (nadie
   intervino realmente, sin diferencia que revisar). `07_cierre_del_dia.md` § "Ver arqueo".
   **B52, B53, C22** (riesgo).
7. **Apertura de la sesión del 23/10** (fondo 2.000.000 Gs.): misma pantalla y mensajes que la
   primera apertura. [00_apertura.md](00_apertura.md) Paso 5, "Registro adicional". **B18 se
   repite**.
8. **Desembolso con la caja del día ya abierta**: las 4 condiciones en verde, con la línea de
   saldo antes/después — contraste directo con el punto 3. "Registro adicional" del Paso 2.
9. **Comprobante de desembolso**: PDF `comprobante_desembolso_2` (`DES-2026-000002`, Sesión
   N.º 3) — misma estructura que el primer cliente. Paso 3. **C12** (contraste: esta vez la
   fecha elegida ya era hábil, sin discrepancia que mostrar) y **B13** (confirmación: domicilio
   con coma duplicada, de nuevo).
10. **Confirmar desembolso**: `CC-2026-00002` pasa a `ACTIVO`, movimiento `DESEMBOLSO` en la
    sesión N.º 3, saldo teórico 0 Gs., numerador `DES` correlativo (`000001` → `000002`). Paso
    4, "Registro adicional". **B22 se repite**.

**Qué sigue**: el préstamo queda `ACTIVO`, con su primera cuota el 23/11/2026 — a cobrar en el
tramo 5 (primer cobro, no recorrido todavía con este cliente) y, eventualmente, a cerrar el día
otra vez en el tramo 6.

## Completitud del tramo 4

Control de completitud contra el código (solo lectura, verificado hoy): todos los caminos que
existen en Caja y Desembolso, marcando cuáles se recorrieron en pantalla y cuáles quedan
pendientes. Detalle de cada camino ya recorrido en los Pasos 1-5 de arriba, en
[00_apertura.md](00_apertura.md) y en [05_que_pasa_por_detras.md](05_que_pasa_por_detras.md).

| Camino | Existe en el código | ¿Se recorrió hoy? |
|---|---|---|
| Desembolsar sin caja abierta | `ServicioDesembolso.asegurarCondicionesDesembolso` rechaza "No hay una caja abierta." | **No recorrido** (C10) |
| Desembolsar con una caja ABIERTA pero de un día anterior | `ServicioCaja.validarSesionDeHoy` → `CajaDeOtroDiaException`, mensaje "La caja del [fecha] sigue abierta: ciérrela y rinda antes de operar hoy." | **Sí — confirmado en pantalla el 23/10/2026** (segundo cliente de práctica, `CC-2026-00002`, con la sesión N.º 2 todavía abierta desde el 22/10): "Condiciones para desembolsar" muestra "No disponible"/"Insuficiente" en las dos condiciones de caja, con el aviso exacto y el enlace "Ir a caja"; no aparece la sección "Confirmar desembolso" (ver la Nota, antes del Paso 3) |
| Desembolsar con saldo insuficiente | `ServicioDesembolso.construirMensajeSaldoInsuficiente` | **No recorrido** (C10) |
| Desembolsar con documentos vencidos y firmados ya cargados | `asegurarCondicionesDesembolso` + `ServicioFormalizacion.asegurarSinDocumentosFirmados` (C11) | **No recorrido** — los documentos de Marta Ovelar se generaron hoy mismo, dentro de los 5 días; además, verificado en el código que **no existe ningún camino de salida puntual** una vez vencidos con firmados cargados (C11) |
| Entrega de efectivo adicional del gerente a una caja ya abierta | `ServicioCaja.registrarEntregaGerente`, exige perfil gerente/administrador (separación de funciones) | **No recorrido** — previsto para el Paso 6 |
| Reimprimir el comprobante de desembolso | `ServicioDesembolso.obtenerPdfComprobanteDesembolso`, vía "Imprimir comprobante" del modal de éxito (Paso 4) o "Reimprimir" en `/caja` (Paso 5) | **Sí** — Paso 4, auditado como `REIMPRIMIR_COMPROBANTE_DESEMBOLSO` (ver B23) |
| Doble confirmación del desembolso (doble clic/volver atrás) | `identificadorConfirmacion` idempotente + restricción `UNIQUE` como red final | **No recorrido en pantalla** — verificado solo en el código (Paso 4) |
| Cancelar en el cuadro de confirmación ("¿Confirmar el desembolso de [monto]?") | Mecanismo genérico de SweetAlert2 (`plantilla/pie.jsp`) — el formulario simplemente no se envía | **No recorrido** — Joel confirmó directo con "Sí, desembolsar" |
| Imprimir el comprobante sin cumplir las 4 condiciones (ej. por URL directa) | `generarComprobantePreDesembolso` también exige `asegurarCondicionesDesembolso` antes de generar el PDF | **No recorrido** — las 4 condiciones ya estaban en verde al imprimir (Paso 3) |
| Desembolsar un préstamo con el checklist incompleto (no debería ni aparecer en la bandeja) | `listarListosParaDesembolsar` filtra por `esChecklistCompleto`; `confirmarDesembolso` revalida lo mismo igual si se fuerza la URL | **No recorrido** — el único préstamo de hoy ya tenía el checklist completo desde el tramo 3 |
| Acceso por URL directa de un rol sin permiso (ej. oficial u gerente a `/desembolso`) | `FiltroAutorizacion` exige `DESEMBOLSAR`, `NINGUNO` para oficial y gerente → 403 | **No recorrido hoy** (patrón ya verificado en otras escenas de este recorrido, no repetido puntualmente para Desembolso) |
| Abrir una segunda caja mientras otra sigue ABIERTA | `ServicioCaja.abrirCaja` rechaza con `CajaYaAbiertaException` ("Ya hay una caja abierta desde...") antes de escribir nada | **No recorrido** — hoy la cajera siempre cerró la sesión anterior antes de abrir la siguiente (sesión 1 cerrada antes de abrir la 2) |
| Cerrar la caja con una diferencia de arqueo (motivo obligatorio) | `ServicioCaja.cerrarCaja` + `validarMotivoDiferencia` | **No recorrido** — previsto para el tramo 6 (cierre del día) |
| Revisar una diferencia de arqueo sin revisar (gerente, observación obligatoria) | `ServicioCaja.marcarDiferenciaRevisada` | **No recorrido** — no hay ninguna diferencia sin revisar hoy (la sesión 1 cerró exactamente en 0, ver la Nota) |

## 30/10/2026 — lo que vi del comprobante, entrando como gerente

Registro con fecha (el estado vive en [FASE2_PLAN.md](FASE2_PLAN.md)).

- **07:03, gerente Ramón Benítez.** Entré a Reportes → Estado de cuenta → `CC-2026-00005` y arriba a la derecha **solo había "Informe PDF" y "Excel/CSV"**: el botón del comprobante de desembolso no estaba. No era un error del día: la pantalla lo mostraba solo al perfil de la cajera, aunque el gerente sí tiene el permiso de esa ruta. Ítem [B94](FASE2_PLAN.md).
- **07:10, el mismo gerente, ya con el botón.** Reimprimí `DES-2026-000004`: página 1 *"ORIGINAL — ARCHIVO CAPITAL CRÉDITO"*, página 2 *"COPIA — CLIENTE"*. Con eso quedó **visto en pantalla** el texto de ejemplar que el 29/10 solo se había visto en la muestra en PNG ([B58](FASE2_PLAN.md)).

## 30/10/2026 (paso 15 b) — el desembolso real de CC-2026-00004

Registro con fecha; el estado de cada ítem vive en [FASE2_PLAN.md](FASE2_PLAN.md).

- **19:20, gerente Ramón.** Cargó en Formalización los tres documentos firmados, y el aviso fue nombrando lo que faltaba en cada paso: *"Faltan: Pagaré firmado, Plan de pagos firmado."* → *"Faltan: Plan de pagos firmado."* → *"Checklist completo: listo para desembolso."*, con la banda verde *"Documentación completa… Siguiente paso: la cajera lo desembolsa desde Desembolso y caja → Desembolsos."* (B88/B89 funcionando).
- **Lo que vi antes de cargarlos**: el panel del gerente decía *"1 préstamo pendiente de desembolso · 0 listos para desembolsar (0 Gs.)"* **sin enlace y sin decir qué falta** — y lo que faltaba le tocaba a él. Anotado como [B100](FASE2_PLAN.md). **RESUELTO el 01/11/2026 y visto en pantalla el mismo día** (panel de Ramón, con `CC-2026-00006` formalizado y sin los firmados): la tarjeta agregó la línea *"falta cargar…"* con los tipos que faltan y el enlace *"Ir a Formalización →"* — el gerente no veía *"Ir a Desembolsos"* porque `/desembolso` es del cajero y del administrador. Dueño del detalle: [RF30_panel.md](../funcionamiento/RF30_panel.md) §2.
- **19:24.** Entregó **1.000.000 Gs.** a la caja (sesión 13, abierta en 0). El cartel *"¿Entregar 1.000.000 Gs. a la caja?"* abre con el foco en **"No, volver"** — es el modelo que anoté en [B86](FASE2_PLAN.md) para los demás carteles que mueven dinero.
- **19:28-19:31, cajera Sofía.** Las 4 condiciones en verde, comprobante **`DES-2026-000005`** impreso para firma (19:28), casilla de conformidad, cartel *"¿Confirmar el desembolso de 1.000.000 Gs.?"* —este abre con el foco en **"Sí, desembolsar"**, lo contrario del anterior— y *"Desembolso de CC-2026-00004 confirmado (1.000.000 Gs.)"*.
- **19:31, reimpresión.** Dice *"Reimpreso por Sofía Martínez el 30/10/2026 19:31."* y el encabezado y *"Emitido por"* muestran **19:31**, que es la hora del **desembolso** (no la de la reimpresión): el papel firmado a las 19:28 decía 19:28 porque ahí todavía no existía la operación. Verificado en el código; **no es un defecto**.
