# RF19 — Cierre de caja con arqueo, motivo de diferencia

**Estado: cerrado y probado en pantalla completo el 11/10/2026 — tag `rf19-probado`.** El motivo
mínimo de 10 caracteres, el tope del efectivo contado, el acta de arqueo y el botón "Sí, cerrar
caja" quedaron confirmados en pantalla. Quedan 3 mejoras menores (sección 10, a-c) y la prueba
de la carrera de dos pestañas, que NO se hacen en esta vuelta.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF19 (módulo Desembolso y caja):

> El sistema debe permitir cerrar la caja con arqueo, comparando el efectivo contado con el
> saldo teórico y exigiendo el motivo cuando hay diferencia.

RF19 cierra el ciclo que abrió RF15 (apertura) y que usaron RF16/RF17/RF18 (desembolso,
movimientos) — mismo archivo, misma pantalla `/caja`.

## 2. Cómo funciona

En `/caja`, con una sesión `ABIERTA`, el formulario "Cerrar caja" (visible para quien no es
gerente) pide un solo dato: "Efectivo contado" (más un campo "Motivo de la diferencia", que solo
hace falta llenar si corresponde). Postea a `POST /caja/cerrar` →
`ServicioCaja.cerrarCaja(idUsuarioCajero, saldoFisico, motivoDiferencia)`:

1. Calcula el saldo teórico (`saldo_inicial + SUM(monto*signo)` de todos los movimientos, misma
   fórmula de RF16/RF18).
2. `diferencia = saldoFisico - saldoTeorico`.
3. Valida el motivo si la diferencia supera la tolerancia (`caja.diferencia_tolerada`, hoy 0).
4. `sesionCajaDAO.marcarCerrada` — `estado_sesion = 'CERRADA'`, con teórico/físico/diferencia/
   motivo y `fecha_hora_cierre = now()`.
5. Inserta un movimiento `RENDICION_GERENTE` (signo −1, por el efectivo físico contado — "la
   salida" simbólica de todo lo que había en la caja).
6. Audita `CERRAR_CAJA` con el resumen completo.

**El pie de firmas del acta (C22, decisión de Joel del 03/11/2026)** — dos columnas: a la
izquierda la raya de la **Cajera**, con *"Entregado por: [nombre]"* debajo; a la derecha la raya
del **Gerente**, con:

- *"Recibí: [nombre]"* **solo** si esa sesión tiene un gerente **registrado**
  (`sesiones_caja.id_gerente_revisor`, que hoy escribe únicamente
  `marcarDiferenciaRevisada` al revisar una diferencia);
- *"Recibí (firma):"* **en blanco** si no hay ninguno — que es el caso de **toda** sesión sin
  diferencia (la gran mayoría) y también el de una diferencia todavía sin revisar. Se firma
  sobre la raya que el pie ya trae; sin guiones bajos, para no dibujar un segundo espacio de
  firma.

**Qué hacía antes y por qué se cambió**: cuando no había gerente registrado, el acta imprimía
*"Recibí: "* + el parámetro `caja.gerente_predeterminado` — o sea, **afirmaba en nombre de una
persona concreta la recepción de un efectivo que esa persona podía no haber contado**, y con un
faltante todavía sin revisar sugería además que ya lo había aceptado. El sistema **no tiene
ningún paso** en el que un gerente confirme la recepción de un cierre, y **RF19 tampoco lo
pide**, así que el documento no puede decir que ocurrió. Se descartó la alternativa de agregar
ese paso (sería un trámite diario más y un requisito nuevo, fuera del anteproyecto).

La línea entera la decide `ServicioCaja.lineaRecibiDelArqueo` (pura) y el `.jrxml` la imprime
tal cual, sin prefijos propios: antes el *"Recibí: "* fijo vivía en la plantilla, que no podía
saber si había alguien que hubiera recibido algo (la misma trampa de B103). **Efecto**:
`caja.gerente_predeterminado` quedó **sin uso** — se conserva por historial y su descripción se
corrige en la limpieza final (ver *"Antes de entregar"* en `AVANCE.md`).

## 3. Auditoría

| Evento | Entidad | Cuándo | Detalle |
|---|---|---|---|
| `CERRAR_CAJA` | SESION_CAJA | Al cerrar | "Cerró la caja. Teórico X, físico Y, diferencia Z[. Motivo: ...]" |
| `REVISAR_DIFERENCIA_CAJA` | SESION_CAJA | Cuando el gerente revisa una diferencia ya cerrada (RF15) | Con la observación obligatoria |

**No hay auditoría de "se vio/descargó el arqueo"** — el PDF se genera al primer pedido y se
guarda (`obtenerOGenerarPdf`), sin pasar por `ServicioAuditoria`; solo el cierre en sí queda
auditado.

## 4. Dónde está

- `servicios/ServicioCaja.java` — `cerrarCaja`, `validarMotivoDiferencia`, `generarPdfArqueo`,
  `obtenerPdfArqueo`, `formatearDiferenciaArqueo` y, desde el 03/11/2026 (**C22**),
  `lineaRecibiDelArqueo` (pura, dueña del texto de la firma del gerente).
- **El acta se guarda, no se regenera**: `obtenerPdfArqueo` → `obtenerOGenerarPdf` sobre
  `sesiones_caja.id_archivo_cierre` ("generar al primer pedido y guardar", D-94). Una sesión
  cuya acta ya se imprimió **conserva para siempre** el formato con el que se generó; por eso
  C22 no alteró ningún documento existente (al 03/11/2026 ninguna de las 14 sesiones tenía su
  acta generada). El reverso, ya conocido como **C27**: si el acta se imprime **antes** de que
  el gerente revise una diferencia, el PDF guardado queda sin su nombre aunque después la
  revise.
- `dao/SesionCajaDAO.java` (`marcarCerrada`).
- `controladores/ControladorCajaServlet.java` (`POST /caja/cerrar`, `GET /caja/{id}/arqueo`).
- `webapp/WEB-INF/vistas/caja/ficha.jsp` (formulario de cierre, lista de "Sesiones cerradas").
- `src/main/resources/reportes/arqueo_caja.jrxml`.
- Parámetro `caja.diferencia_tolerada` (hoy 0); tabla `sesiones_caja`.

## 5. Dependencias

- **Depende de**: RF15 (la sesión tiene que existir y estar abierta), RF16/RF18
  (`sumarMovimientos`, la misma fórmula del saldo teórico que usan los otros RF de caja).
- **Lo afecta un cambio en**: `MovimientoCajaDAO.sumarMovimientos` (fórmula única, compartida).
- **A quién afecta un cambio acá**: RF15 (una vez cerrada, es la única forma de volver a abrir
  una caja nueva); la revisión de diferencias del gerente (mismo documento, decisión 4 del plan
  de E6, ya cubierta en `RF15_apertura_caja.md`).

## 6. Análisis

### 11/10/2026 — revisión completa de RF19 contra el texto firmado (pedido de Joel, solo lectura)

**Método**: lectura completa de `ServicioCaja.cerrarCaja`/`validarMotivoDiferencia`/
`generarPdfArqueo`, `SesionCajaDAO.marcarCerrada`, `MovimientoCajaDAO.insertar`,
`ControladorCajaServlet.java`, `caja/ficha.jsp`, `arqueo_caja.jrxml`,
`infraestructura/ConversorNumeros.java`, `infraestructura/NormalizadorTexto.java`, y
`bd/01_esquema.sql` (tabla `sesiones_caja`).

### a) Quién puede cerrar y qué se guarda

**Quién**: misma matriz que RF15 — `Permiso.ABRIR_CERRAR_CAJA` es `TOTAL` solo para la cajera
(y el administrador); el gerente tiene `CONSULTA` (puede ver `/caja`, pero cualquier `POST`
exige `TOTAL` y le da 403). El formulario de cierre en la pantalla, además, solo se renderiza
para `!esGerenteCaja` — doble capa, pantalla y servidor, mismo criterio de siempre.

**Qué se guarda** (`sesiones_caja`, al cerrar): `saldo_fisico` (lo contado), `saldo_teorico_cierre`
(calculado, congelado en ese momento), `diferencia_arqueo` (`físico - teórico`),
`motivo_diferencia` (si hizo falta), `fecha_hora_cierre`, `estado_sesion = 'CERRADA'`.
**Quién cerró** NO queda en una columna propia de `sesiones_caja` (esa tabla solo tiene
`id_cajero`, fijado al ABRIR) — queda en la auditoría (`CERRAR_CAJA`, actor = quien cerró) y en
el movimiento `RENDICION_GERENTE` (`registrado_por` = quien cerró) — así que, si una cajera
distinta a la que abrió fuera la que cierra, ambos datos (quién abrió, quién cerró) quedan
igual de trazables, solo que en lugares distintos.

### b) Motivo obligatorio con diferencia

**Implementado el 11/10/2026** (sección 7, punto 1): mínimo de 10 caracteres reales, con la
`MotivoDiferenciaInsuficienteException` y el aviso en vivo — el análisis original de este
hallazgo (sin la corrección) queda abajo, tal como se escribió antes de la decisión de Joel.

**¿Se controla en el servidor?** Sí — `validarMotivoDiferencia(diferencia, tolerancia, motivo)`
(pura) corre dentro de `cerrarCaja`, no solo en la pantalla: `if (diferencia.abs() >
tolerancia && (motivo == null || motivo.isEmpty())) throw ...` — *"La diferencia de arqueo (X)
supera lo tolerado (Y); ingrese el motivo."*

**¿Hay un largo mínimo, o se acepta "." o espacios?** El motivo pasa antes por
`NormalizadorTexto.normalizarTextoLibre` (recorta espacios al principio/final y colapsa
espacios dobles) — así que un motivo de solo espacios (`"   "`) queda `""` y SÍ lo rechaza. Pero
**no hay ningún largo mínimo ni control de "contenido con sentido"**: un motivo de un solo
carácter, como `"."`, pasa la validación sin problema (no es `null` ni `""`) — la diferencia se
guarda igual, con ese motivo literal. RN-23 pide "toda diferencia exige motivo", y
técnicamente `"."` es "un motivo" — cumple la letra, no necesariamente la intención. No es un
hallazgo que requiera corrección automática (decidir cuánto exigir es una decisión de negocio,
no algo que el código pueda inferir solo), pero queda documentado para que Joel decida si vale
la pena un mínimo de caracteres.

**¿La diferencia puede ser sobrante (+) y faltante (−), y se muestran distinto?** Sí —
`diferencia = saldoFisico - saldoTeorico`: positiva = sobrante, negativa = faltante.
`ServicioCaja.formatearDiferenciaArqueo` (usada en la pantalla, el PDF del arqueo, y el informe
de caja, los 3 con el mismo texto) muestra *"Sobra X Gs."* si es positiva, *"Falta X Gs."* si es
negativa, o el monto plano (0) si no hay diferencia — nunca solo el número con signo.

### c) Validaciones del efectivo contado

`ConversorNumeros.aBigDecimalGuaranies` (compartido con cualquier campo de monto del sistema,
no solo este) + el chequeo propio de `cerrarCaja`:

| Caso | Qué pasa |
|---|---|
| Vacío | "Ingrese un monto." (el conversor, antes de llegar a `cerrarCaja`) |
| Letras | "\"texto\" no es un monto válido (los guaraníes no llevan decimales)." — el regex exige solo dígitos (y un signo "-" opcional) después de quitar los puntos de miles |
| Decimales (coma o punto que no sea separador de miles) | Mismo mensaje de arriba — cualquier carácter que no sea dígito después de sacar los puntos (incluida una coma decimal) hace fallar el regex |
| Negativo | **Sí se parsea** (el regex permite un "-" inicial) — pero `cerrarCaja` lo rechaza aparte: `if (saldoFisico == null \|\| saldoFisico.compareTo(BigDecimal.ZERO) < 0) throw "Ingrese el saldo físico contado."` — dos capas, una sintáctica (el conversor) y una de negocio (el servicio) |
| Montos enormes | Sin tope explícito en el código — la columna es `NUMERIC(18,0)` (hasta 18 dígitos); en la práctica, cualquier número así de descomunal generaría una diferencia enorme que de todos modos exige motivo, sin romper nada por sí solo |

### d) "Ver arqueo"

El PDF (`generarPdfArqueo`) trae: datos de la empresa, N.º de sesión, cajera, apertura/cierre,
fondo inicial, cada tipo de movimiento con su cantidad y monto (entregas, cobros, anticipos,
cancelaciones, vueltos, desembolsos, compensaciones), saldo teórico, efectivo contado,
diferencia (con "Sobra"/"Falta"), motivo si hubo, composición del total aplicado (capital,
interés, moratorio, punitorio, IVA), rangos de recibos/facturas/constancias/comprobantes de
desembolso emitidos en la sesión — **y, al final, un acta con línea de firma para "Cajera" y
"Gerente"**, con los textos "Entregué: [nombre de la cajera]" / "Recibí: [nombre del gerente que
revisó la diferencia, o el gerente predeterminado si no hubo diferencia]" — exactamente lo que
Joel esperaba ("acta de arqueo con firma de la cajera y el gerente").

**¿Se puede imprimir o exportar?** Se descarga como archivo (`Content-Disposition: attachment`,
`GET /caja/{idSesion}/arqueo`) — a diferencia de otros PDF del sistema (documentos, comprobante
de desembolso) que abren `inline`; este se guarda a disco y se imprime desde ahí. Generado una
sola vez (al primer pedido) y reutilizado después — nunca se regenera con datos distintos
(coherente con que una sesión `CERRADA` es inmutable, RN-14).

**¿Queda auditoría del cierre?** Sí, del CIERRE en sí (sección 3) — no de cada vez que alguien
abre o descarga el PDF del arqueo después.

### e) Qué pasa después del cierre

- **¿Se puede reabrir la misma caja?** No — `CERRADA` es terminal; no existe ningún método que
  vuelva a poner `estado_sesion = 'ABIERTA'`. Lo único que puede cambiar en una sesión ya
  cerrada es `gerente_revisa_id` (`marcarRevisadaPorGerente`, una sola vez, RF15).
- **¿Se puede abrir otra el mismo día?** Sí, sin límite — `abrirCaja` solo exige que no haya
  OTRA sesión `ABIERTA` en ese momento; nada impide abrir, cerrar, y volver a abrir varias veces
  el mismo día calendario.
- **¿Qué pasa con los cobros y desembolsos si no hay caja?** Los dos quedan bloqueados en el
  servidor con el mismo mensaje: *"No hay una caja abierta."* (`ServicioDesembolso.confirmarDesembolso`,
  y `ServicioCobranza` en al menos dos puntos de su propio flujo de cobro) — sin caja abierta,
  ninguna operación de efectivo puede registrarse.

### f) Doble cierre y CSRF

**Implementado el 11/10/2026** (sección 7, punto 2): `marcarCerrada` ahora es condicional
(`WHERE ... AND estado_sesion = 'ABIERTA'`), mismo patrón que `marcarDecidida` (RF09) — el
análisis original de este hallazgo (sin la corrección) queda abajo, tal como se escribió antes
de la decisión de Joel.

**Hallazgo — a diferencia de los demás casos de "doble envío" ya documentados en este
proyecto (RF09/RF10 aprobar-rechazar, RF12 número de operación, RF15 abrir caja, RF16
desembolsar), el cierre de caja NO tiene ningún respaldo de base de datos contra una carrera
real.** `SesionCajaDAO.marcarCerrada` es un `UPDATE ... WHERE id_sesion_caja = ?` **sin**
`AND estado_sesion = 'ABIERTA'`, y no hay ninguna restricción `UNIQUE` que impida que la lógica
de cierre se ejecute dos veces sobre la misma sesión. En el caso NORMAL (secuencial — un clic
termina antes de que llegue el segundo), el chequeo inicial de `cerrarCaja`
(`sesionCajaDAO.obtenerAbierta`) ya protege: la segunda petición no encuentra ninguna sesión
`ABIERTA` (la primera ya la cerró) y rechaza con *"No hay una caja abierta."* — pero en una
carrera REAL (dos pestañas, casi al mismo instante, las dos leen la sesión todavía `ABIERTA`
antes de que cualquiera confirme), **las dos pueden terminar ejecutando el cierre completo**:
dos `UPDATE` sobre la misma fila (el segundo simplemente repite los mismos valores si los
montos coincidían, o los pisa con los del segundo clic si no), **y dos movimientos
`RENDICION_GERENTE` insertados** (`MovimientoCajaDAO.insertar` tampoco tiene ninguna guarda) —
quedando dos eventos `CERRAR_CAJA` en la auditoría para un solo cierre real. No corrompe el
saldo de sesiones FUTURAS (la sesión ya quedó `CERRADA` y nunca se vuelve a usar para calcular
nada), pero sí deja un registro duplicado y potencialmente confuso. **Mismo patrón que ya se
documentó, sin corregir, para `marcarDecidida`/`marcarDesembolsado`/`marcarCerrada`** — con la
diferencia de que, en este caso puntual, no hay ningún índice único que termine impidiendo el
duplicado como backstop final.

**CSRF**: el formulario de cierre incluye el token (`plantilla/csrf.jsp`), igual que el resto.

### g) Cumplimiento

El texto se cumple **sin excepción**: se puede cerrar la caja con arqueo, comparando el
efectivo contado contra el saldo teórico, exigiendo el motivo cuando la diferencia supera la
tolerancia — todo validado en el servidor. El hallazgo de la sección 6f (doble cierre sin
respaldo de base) no es un incumplimiento del texto (que no habla de concurrencia), pero es una
diferencia real respecto al resto del sistema, señalada para que Joel decida si vale la pena
cerrarla.

### h) Prueba propuesta (respuesta a Joel)

**Actualizado el 11/10/2026 tras la decisión de Joel (sección 7, punto 1)** — el paso 2 ya no
se acepta como se describió originalmente; con el mínimo de 10 caracteres implementado, el
resultado correcto ahora es:

1. Cerrar contando 10.000 **sin motivo** (saldo teórico 0 → diferencia +10.000, supera la
   tolerancia 0) → bloqueado, como advertencia, con "Hay una diferencia de 10.000 Gs. (Sobra).
   Explique el motivo con al menos 10 caracteres."
2. Motivo "." → **se rechaza**, con el mismo mensaje de advertencia del paso 1 (1 carácter, muy
   por debajo del mínimo de 10) — distinto del comportamiento original analizado (que sí lo
   aceptaba, sección 6b), ya corregido.
3. Motivo válido de al menos 10 caracteres (ej. "Faltó contar un billete, se encontró
   después.") → cierra con diferencia +10.000, mostrada como "Sobra 10.000 Gs." en pantalla y
   en el PDF.
4. "Ver arqueo" → PDF con el resumen completo y el acta de firma Cajera/Gerente.

## 7. Decisiones

Decisiones del 11/10/2026 (Joel), sobre los 2 hallazgos de la sección 6 — todas implementadas:

1. **Motivo del cierre con diferencia** (servidor y pantalla): `ServicioCaja.validarMotivoDiferencia`
   ahora exige un mínimo de 10 caracteres reales (sin contar espacios al inicio/final — el
   llamador ya normaliza antes de validar), no solo que el campo no esté vacío — así, "." o
   "aaa" ya no alcanzan. Lanza la nueva `MotivoDiferenciaInsuficienteException` (subtipo de
   `NegocioException`, mismo patrón que `SolicitudYaDecididaException` de RF09) con el texto
   exacto pedido: *"Hay una diferencia de [monto] Gs. ([Sobra/Falta]). Explique el motivo con
   al menos 10 caracteres."* — `ControladorCajaServlet` la distingue de una `NegocioException`
   cualquiera y la muestra como advertencia (`MensajesFlash.guardarAdvertencia`, ícono y botón
   "Entendido"), no como error. En la pantalla, un aviso en vivo bajo el campo de motivo cuenta
   los caracteres reales mientras se escribe ("3 de 10 caracteres mínimos" → "✓ 12 caracteres"),
   solo cuando hay diferencia — informativo, el control real sigue siendo del servidor.
2. **Cierre contra dos pestañas**: `SesionCajaDAO.marcarCerrada` pasa a ser condicional
   (`UPDATE ... WHERE id_sesion_caja = ? AND estado_sesion = 'ABIERTA'`, devuelve `boolean`),
   mismo patrón que `SolicitudDAO.marcarDecidida` (RF09). Si pierde la carrera,
   `ServicioCaja.cerrarCaja` no inserta la rendición ni la auditoría — tira
   `CajaYaCerradaException` (mismo patrón de subtipo) con *"Esta caja ya fue cerrada por
   [nombre] el [fecha y hora]. Actualice la página."*, resuelta releyendo la sesión y buscando
   su movimiento `RENDICION_GERENTE` (su `registrado_por` es quién cerró — `sesiones_caja` no
   tiene una columna propia para eso, ver sección 6a). Mostrada como advertencia, igual que el
   punto 1.
3. **Efectivo contado**: nuevo parámetro `caja.monto_maximo_arqueo` (500.000.000 Gs.) — si el
   efectivo contado lo supera, `cerrarCaja` rechaza con un mensaje claro que menciona "ceros de
   más", antes de tocar nada.
4. **Botones**: `data-confirmar-boton` en los 4 formularios de caja/desembolso — "Sí, cerrar
   caja" (ya tenía confirmación, solo le faltaba el texto propio), "Sí, abrir caja" (no tenía
   NINGUNA confirmación — se le agregó una, con el fondo tecleado, mismo patrón que "Entregar
   efectivo"), "Sí, entregar" y "Sí, desembolsar" (RF17).

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 15/11/2026 | **B191** (tanda 2) y la **corrección de B188**. **B191**: *"Sesiones cerradas"* dejó de mostrar solo *"las últimas 10"* — se traen **todas** (`SesionCajaDAO.listarCerradas(conexion)`, el mismo `ORDER BY` sin el `LIMIT`, así que la página 1 trae las 10 de siempre y 10 más), con contador y paginación de 20; y *"Movimientos"* también, con dos cuidados propios: la fila del **fondo inicial** va solo en la **página 1** (si no, sería la única fila repetida en cada página) y **no se cuenta**, porque no es un movimiento (vive en `sesiones_caja.saldo_inicial`, B.14). Son **dos** listados en una pantalla, así que cada uno tiene su parámetro (`paginaSesiones`, `paginaMovimientos`) y la paginación va con `<jsp:include>` y sin pasar por `listado-vivo.js`, para lo que B183 ya había preparado el fragmento. **Corrección de B188** (defecto que Joel vio al probarlo): el cuerpo de la ventana pegaba un punto al motivo escrito por la cajera y con un motivo ya cerrado quedaba *"…a propósito.."*; ahora lo cierra `FormateadorTexto.conPuntoFinal`, que agrega el punto solo si falta, con 5 JUnit. `mvn test` 1022/0. Estado y detalle, en `FASE2_PLAN.md`. | *(este commit)* | — |
| 15/11/2026 | **B187 + B188** (tanda 1 de la revisión UX de Desembolso y caja). **B187**: el cartel del cierre nombra el **día** de la caja que se cierra (*"¿Cerrar la caja del 13/11/2026 con el efectivo contado?"*) — antes no lo decía, y con una caja de otro día abierta era justo el dato que faltaba. Va en el **título**: el cuerpo lo reescribe el script de la pantalla en cada tecla, con el efectivo contado y la diferencia. **B188**: *"Marcar revisada"* pasó de un formulario en línea sin confirmación a una **ventana por diferencia** (patrón de B155), con el día, la cajera, el signo, el monto y el motivo que dejó la cajera, y el foco en *"No, volver"* — marcar revisada no se deshace (`DiferenciaYaRevisadaException`). El formulario, el campo `observacion`, el CSRF y la ruta POST no cambian, así que el scraping del humo (línea 3238) sigue andando. Ni `cerrarCaja` ni `marcarDiferenciaRevisada` se tocaron. `mvn test` 1017/0. Estado y detalle, en `FASE2_PLAN.md`. | *(este commit)* | — |
| 03/11/2026 (tanda 3) | **C22**: el pie del acta deja de afirmar una recepción que nadie hizo — *"Entregado por: [cajera]"* y *"Recibí: [gerente]"* **solo** si hay uno registrado en la sesión; si no, *"Recibí (firma):"* en blanco (ver §2). La línea la decide `ServicioCaja.lineaRecibiDelArqueo` (pura) y el `.jrxml` la imprime tal cual (parámetro `RECIBI_LINEA`, reemplaza a `GERENTE_RECIBIO`). `arqueo_caja.jasper` recompilado solo para este reporte, una línea del manifiesto. `caja.gerente_predeterminado` queda sin uso. **Probado en pantalla el mismo día** (§9). `mvn test` 907/907, 4 JUnit nuevos | `bbcb126` | — |
| 11/10/2026 | **Reprueba del Lote 1: A.9 confirmado OK en pantalla** (la pantalla frena el motivo corto antes de llegar al servidor). Hallazgo nuevo (D.5): el cartel de `pie.jsp` repetía la etiqueta COMPLETA del campo (con la aclaración entre paréntesis) — corregido a "El motivo de la diferencia necesita al menos 10 caracteres (tiene 3)." (genérico, afecta a todo el sistema) | *(pendiente)* | — |
| 11/10/2026 | Consolidación RF08-RF19 (Lote 1): la pantalla ya bloquea un motivo corto (minlength + required, A.9), el aviso nativo del navegador se reemplazó por el cartel propio en todo el sistema (A.9), y "esta diferencia ya fue revisada" (carrera de dos pestañas al revisar) pasa a advertencia, no error (A.8, `DiferenciaYaRevisadaException`) | *(pendiente)* | — |
| 11/10/2026 | Probado en pantalla completo: motivo mínimo rechazado en el servidor, cierre con diferencia sobrante, acta de arqueo, botón "Sí, cerrar caja", contador en vivo | *(pendiente)* | `rf19-probado` |
| 11/10/2026 | Implementadas las decisiones de la sección 7 (motivo mínimo de 10 caracteres con advertencia, cierre protegido contra dos pestañas, tope de efectivo contado, 4 botones con texto propio), 9 pruebas nuevas, `mvn test` 666 en verde | *(pendiente)* | — |
| 11/10/2026 | Análisis completo contra el texto firmado, sin cambios de código | *(pendiente)* | — |
| 07/11/2026 | **B61** (B86, tramo 3): la lista de *"Sesiones cerradas"* dice los **tres** estados de revisión, no uno. Antes el único texto era *(revisada)*, así que su ausencia cubría dos casos que no se distinguían: *"no hace falta revisarla"* y *"falta que el gerente la revise"*. **La regla, verificada en el código antes de elegir el texto: solo una diferencia distinta de cero se revisa** — `SesionCajaDAO.listarConDiferenciaSinRevisar` filtra `diferencia_arqueo <> 0 AND id_gerente_revisor IS NULL` (RN-23) y `ServicioCaja.marcarDiferenciaRevisada` rechaza una caja sin diferencia (*"Esta caja no tiene diferencia para revisar."*). Quedó: **0 Gs.** → sin texto (la columna "Diferencia" ya lo dice); **≠ 0 revisada** → *(revisada)*; **≠ 0 sin revisar** → *(pendiente de revisión del gerente)* en ámbar. El modelo fue el **Informe de Caja**, que ya imprimía la columna "revisada" con "Sí"/"No" (`informe_caja.jrxml:175`). **Solo presentación**: no cambió ninguna consulta, ninguna regla y ningún permiso; quién puede revisar y cuándo sigue decidiéndolo `ServicioCaja`. Probado en pantalla por Joel el mismo día, cerrando una caja con 10.000 Gs. de falta (§9) | `c7ae9b3` | — |
| 07/11/2026 | **B52 + B53** (B86, tramo 3): el **detalle de movimientos del arqueo** muestra **día y hora** y un **cero sin signo**. **B52**: la expresión `(signo < 0 ? "-" : "") + monto` no miraba el monto, así que una rendición al gerente de cero salía *"−0 Gs."* y se leía como un error de cuentas (hay **11** movimientos así en la base). Estaba escrita **dos veces** —la tabla de movimientos de la pantalla y el detalle del arqueo— y las dos pasaron a llamar a `ServicioCaja.montoConSigno(signo, monto)`, puro y con 3 JUnit. **B53**: un `.substring(11)` dejaba solo `hh:mm`; ahora imprime `dd/mm/aaaa hh:mm`, y para eso la columna del `.jrxml` pasó de **50 a 80 px** con las demás corridas (hora 0/80, tipo 80/100, operación 180/125, comprobante 305/85, monto sin cambios en 390/91) — JasperReports no recorta, corta en seco, y los anchos se midieron contra el dato más largo real. Se recompiló solo `arqueo_caja`: un `.jasper` y una línea del manifiesto. **Ningún cálculo, ningún total, ninguna consulta**: solo lo que se imprime. **Los 15 arqueos ya emitidos no cambian** (D-94): el formato nuevo vale para los que se emitan de ahí en adelante. Verificado con los **PDF reales** de las sesiones 58 y 60, generados sin congelarlas (se llamó al generador, no a `obtenerPdfArqueo`) y después emitidos de verdad por Joel (§9) | `d0a6f2b` | — |

## 9. Pruebas realizadas en pantalla

**03/11/2026 08:05-08:18 (Joel) — C22, el pie de firmas del acta.** Las dos ramas, sobre
sesiones **ya cerradas** cuya acta nunca se había impreso (así la primera impresión guardó
directamente el formato nuevo, D-94):

- **Sesión 14** (abierta 01/11 19:40, cerrada 03/11 06:17, sin diferencia y sin revisor):
  *"Entregado por: Sofía Martínez"* / **"Recibí (firma):" sin nombre**. Saldo teórico 844.000,
  contado 844.000, diferencia 0, aplicado 844.004, redondeo −4. **OK.**
- **Sesión 9** (28/10, sobra 10.000, diferencia **ya revisada** por el gerente):
  *"Entregado por: Sofía Martínez"* / **"Recibí: Ramón Benítez"**. **OK.**
- Las dos actas quedaron **guardadas por primera vez** con el formato nuevo (`id_archivo_cierre`
  pasó de nulo a su archivo): de acá en adelante, esas dos se reimprimen siempre así.

**11/10/2026 (Joel), después del Clean and Build, cajera Sofía Martínez, caja sesión N.º 2
(saldo teórico 0):**
- **Contado 100.000 sin motivo**: la pantalla frenó con el aviso nativo del navegador
  ("Completa este campo") — no llegó a mandar el `POST` (hallazgo b, sección 10).
- **Motivo "."**: la pantalla dejó pasar hasta el cartel "Sí, cerrar caja" (el `required` del
  HTML solo exige que no esté vacío, no el mínimo de caracteres — hallazgo a, sección 10); el
  **servidor** rechazó con la advertencia exacta *"Hay una diferencia de 100.000 Gs. (Sobra).
  Explique el motivo con al menos 10 caracteres."* y botón "Entendido" — la caja siguió abierta
  y conservó los valores tecleados (`guardarValoresCierre`) — confirma la sección 6b/7 punto 1
  completa.
- **Motivo "Prueba RF19: sobrante de prueba."** (32 caracteres): cerró con *"Caja cerrada...
  diferencia 100.000 Gs. (sobrante). Queda pendiente de revisión del gerente."*; en "Sesiones
  cerradas" se vio "Sobra 100.000 Gs." en color — confirma `formatearDiferenciaArqueo` y el
  cierre en sí.
- **Ver arqueo** (`arqueo_2.pdf`): resumen completo, saldo teórico 0, contado 100.000,
  diferencia "Sobra 100.000 Gs.", motivo, comprobantes (DES-2026-000001), detalle de
  movimientos con "Rendición gerente −100.000", firmas "Entregué: Sofía Martínez" / "Recibí:
  Ramón Benítez" — confirma el acta completa de la sección 6d.
- **Botón "Sí, cerrar caja"**: verificado en el cartel de confirmación.
- **Contador en vivo**: verificado — "1 de 10 caracteres mínimos" mientras el motivo era corto,
  "✓ 32 caracteres" con el motivo válido.

**Resultado: RF19 queda probado completo.** 3 mejoras menores encontradas (sección 10, a-c), NO
corregidas en esta vuelta. La carrera de dos pestañas al cerrar (sección 7, punto 2) quedó
implementada pero sin probar en pantalla todavía.

**13/10/2026 (Joel), después de Clean and Build — Tanda 1 de mejoras pendientes:**
- "Marcar revisada" con el campo vacío: el cartel genérico (`pie.jsp`) ya dice "Falta completar
  «Qué se hizo con la diferencia»" con "Entendido", en vez de "este campo" — confirma el
  `<label>` agregado en `caja/ficha.jsp` (ver sección 10, resuelto).

### 27/10/2026 (Joel), cajera Sofía Martínez — cierre de la sesión 7 con **8 movimientos reales y diferencia 0**

La prueba del 11/10/2026 cerró una caja con **saldo teórico 0** y un sobrante inventado: probó el
camino de la diferencia, pero no el arqueo de una jornada con dinero de verdad. Esta sesión sí:
**7 cobros, un vuelto y la rendición**, con la caja abierta a las **04:27** y cerrada a las
**14:08** del mismo día. Es también el primer cierre que incluye **mora cobrada** (ver
`RF22_cobro_efectivo.md` y `RF24_seguimiento_mora.md`).

**Arqueo, verificado a mano por Joel y después contra la base (solo lectura):**

| Concepto | Movimientos | Monto |
|---|---|---|
| Anticipos de capital | 4 | 835.400 |
| Cobros de cuotas | 2 | 180.700 |
| Cancelación total | 1 | 516.350 |
| Vuelto (salida) | 1 | −50 |
| **Saldo teórico al cierre** | | **1.532.400** |
| **Efectivo contado** | | **1.532.400** |
| **Diferencia de arqueo** | | **0** |

Con saldo inicial **0**, `sum(monto × signo)` de los 9 movimientos de la sesión (los 8 de arriba
más la rendición de −1.532.400) da **exactamente 0**: la caja quedó en cero, sin motivo de
diferencia que completar.

**La otra cuenta, la que cierra por composición** — lo que el motor aplicó a las cuotas, sumado
desde `aplicaciones_pago`:

| Concepto | Monto |
|---|---|
| Capital | 1.503.585 |
| Interés compensatorio | 25.552 |
| **Moratorio** | **542** |
| **Punitorio** | **163** |
| IVA (de interés + moratorio + punitorio) | 2.626 |
| **Total aplicado** | **1.532.468** |
| Redondeos a favor del cliente (D-141) | **−68** (46 + 22) |
| **Efectivo neto** | **1.532.400** |

Las dos cuentas —la de caja y la de composición— llegan al mismo número por caminos distintos.
Esa es la comprobación que faltaba: **el redondeo del efectivo de D-141 no descuadra el arqueo**,
porque lo que entra a la caja es el monto redondeado y lo que se aplica a las cuotas es el exacto.

**Documentos de la jornada**: recibos **REC-2026-000005 a REC-2026-000011** (7, correlativos),
facturas **001-001-0000002 a 0000005** (4; el cobro de REC-2026-000010 salió **sin factura**, por
no tener ningún concepto gravado), y la constancia de cancelación **CAN-2026-000002**. Acta de
arqueo guardada (`sesiones_caja.id_archivo_cierre = 55`) y **rendición al gerente con firmas**.

**Un detalle del acta, para que no se lea mal**: `sesiones_caja.id_gerente_revisor` quedó en
`NULL` — con diferencia 0 no hay nada que el gerente deba revisar, así que el acta nombra al
gerente del parámetro `caja.gerente_predeterminado` ("Ramón Benítez") en el "Recibí", que es
exactamente para lo que ese parámetro existe. No es un dato faltante.

**Con esto RF19 queda probado también sobre una jornada real con movimientos**, y no solo sobre
el camino de la diferencia.

**Evidencia**: `evidencias\2026-10-27\caja\arqueo_sesion_7.pdf` — el acta tal como la emitió el
sistema, exportada desde `archivos_binarios` antes de la limpieza de la base (SHA-256 verificado
contra la base). Ver `evidencias\2026-10-27\LEEME.md`.

### 27/10/2026 22:23 (Joel) — cierre de la sesión 8: **una sola operación, y la mora dentro del arqueo**

La sesión 7 (más arriba) probó el arqueo de una **jornada larga** con 8 movimientos. Esta probó el
otro extremo y suma algo que la 7 no tenía: **una caja abierta y cerrada en 5 minutos para una
sola operación**, y un arqueo cuya composición incluye **moratorio y punitorio cobrados dentro de
la liquidación de una cancelación total** (el paso 16 de `RF22_cobro_efectivo.md` §10 — ver
`RF26_cancelacion_anticipada.md` §9).

Abierta **22:18:05**, cerrada **22:23:27**, saldo inicial **0**.

| | |
|---|---|
| Cobro (cancelación total de `CC-2026-00001`) | 2.433.450 |
| Rendición al gerente | −2.433.450 |
| **Saldo teórico al cierre** | **2.433.450** |
| **Efectivo contado** | **2.433.450** |
| **Diferencia de arqueo** | **0** |

Con solo dos movimientos, `sum(monto × signo)` de la sesión da **0**: la caja quedó en cero y no
hubo motivo de diferencia que completar.

**La composición, que es la parte nueva** — sumada desde `aplicaciones_pago`:

| Concepto | Monto |
|---|---|
| Capital | 2.315.815 |
| Interés compensatorio | 97.938 |
| **Moratorio** | **6.932** |
| **Punitorio** | **2.079** |
| IVA | 10.695 |
| **Total aplicado** | **2.433.459** |
| Redondeo a favor del cliente (D-141) | **−9** |
| **Efectivo neto** | **2.433.450** |

El interés compensatorio de 97.938 es la suma de las tres cuotas que lo tenían (46.589 + 41.815 +
9.534, las dos vencidas más el devengado del período en curso). **Las dos cuentas vuelven a
cerrar en el mismo número por caminos distintos** — la de caja y la de composición — ahora con
mora adentro: confirma de nuevo que el redondeo de D-141 no descuadra el arqueo, con un monto 60 %
mayor que el de la sesión 7 y un redondeo distinto (9 en vez de 68).

**Documentos de la sesión**: recibo **`REC-2026-000012`** (con el detalle de las 9 cuotas),
factura **001-001-0000006** (7 renglones) y constancia **CAN-2026-000003**. Acta guardada
(`sesiones_caja.id_archivo_cierre = 60`).

Igual que en la sesión 7, `id_gerente_revisor` quedó en `NULL` —con diferencia 0 no hay nada que
revisar— y el acta nombra en el "Recibí" al gerente de `caja.gerente_predeterminado`.

**Evidencia**: `evidencias\2026-10-27\caja\arqueo_sesion_8.pdf` (SHA-256 verificado contra la
base). Ver `evidencias\2026-10-27\LEEME.md`.

**Con las dos sesiones, RF19 queda probado en pantalla en sus dos formas**: la jornada larga con
muchos movimientos y la caja corta de una sola operación, las dos con diferencia 0 — además del
camino de la diferencia, que ya estaba probado desde el 11/10/2026.

## 10. Pendientes y trabajo futuro

Mejoras menores encontradas en la prueba de pantalla del 11/10/2026 — **NO corregidas en esta
vuelta** (para la tanda de consolidación):

a) ~~La pantalla debe impedir confirmar si el motivo tiene menos de 10 caracteres~~ —
   **corregido el 11/10/2026** (consolidación RF08-RF19, Lote 1, A.9): el campo motivo ahora
   activa `minLength` por JS junto con `required` (solo cuando hay diferencia), así que un
   motivo corto bloquea el envío antes de llegar al cartel de confirmación. **Confirmado OK en
   la reprueba del Lote 1 (11/10/2026, Joel): la pantalla frena antes de llegar al servidor.**
b) ~~Reemplazar el aviso nativo del navegador ("Completa este campo") por el cartel propio~~ —
   **corregido el 11/10/2026** (A.9): `plantilla/pie.jsp` agrega un manejador genérico del
   evento `invalid` (captura, sin burbuja nativa) para TODO el sistema — cualquier campo
   `required`/`minlength` que falle muestra el cartel propio (ícono de advertencia, "Entendido"),
   nunca el mensaje nativo del navegador. **Hallazgo de la reprueba (D.5): el cartel repetía la
   etiqueta completa del campo (con la aclaración entre paréntesis) — corregido a "El motivo de
   la diferencia necesita al menos 10 caracteres (tiene 3)."**, pendiente de reprueba puntual de
   esta corrección.
c) Acta de arqueo (`arqueo_caja.jrxml`): "Cajero/a" → "Cajera" (texto fijo, no depende del
   perfil real); "N°" → "N.º" (consistencia con el resto de los documentos, RF11/RF12); agregar
   el logo de la empresa (mismo hallazgo que ya se anotó para pagaré y plan de pagos en
   `RF12_documentos_pdf.md` §10e — este reporte tampoco lo declara como parámetro). **Lote 2**
   (C.18), no tocado todavía.
- **Pendiente de pantalla**: la carrera de dos pestañas al cerrar caja (sección 7, punto 2) —
  implementada, sin probar todavía; se verifica con dos pestañas abiertas sobre la misma sesión,
  cerrando casi al mismo tiempo desde las dos.
- **Antes de entregar**: aplicar `bd/migraciones/E10_13_parametro_monto_maximo_arqueo.sql`
  también a `sgcpc_db` (la base real) — hoy solo se aplicó a `sgcpc_db_pruebas`, igual que el
  resto de las migraciones de esta serie.
- La carrera de dos pestañas al cerrar caja queda cerrada igual que `marcarDecidida` (RF09) —
  sigue pendiente, sin programar, el resto de la lista de "otros cambios de estado con el mismo
  patrón" ya inventariada en `RF09_aprobar.md` §12 (no incluye `marcarCerrada`, que ya quedó
  resuelto acá).
- ~~**(12/10/2026, Joel) Campo "Qué se hizo con la diferencia" sin etiqueta**: el campo
  `observacion` del formulario "Marcar revisada" (`caja/ficha.jsp`, revisión de diferencia por el
  gerente) solo tiene `placeholder`, sin `<label>`~~ — **resuelto y probado en pantalla el
  13/10/2026**: mismo arreglo ya aplicado en RF14 para `campoMotivoAnular` (`<label
  class="sr-only">`, sin cambiar el layout) — el cartel ya dice "Falta completar «Qué se hizo
  con la diferencia»".

## 11. Guion para la defensa

**Confirmado en pantalla el 11/10/2026 (sección 9), con la caja sesión N.º 2 — salvo los puntos
5 y 6 (tope de efectivo contado, carrera de dos pestañas), todavía no probados.**

1. Cerrar sin motivo con diferencia — mostrar la advertencia ("Entendido") con el monto y
   "Sobra"/"Falta".
2. Escribir un motivo corto (ej. "." o "aaa") — mostrar el contador en vivo bajo el campo
   ("X de 10 caracteres mínimos") y que el servidor igual rechaza con el mismo texto.
3. Completar un motivo de al menos 10 caracteres — mostrar que el contador pasa a "✓ X
   caracteres" y que el cierre se confirma, con "Sí, cerrar caja" en el cartel.
4. Descargar el arqueo — mostrar el acta con las firmas de cajera y gerente.
5. Intentar cerrar con un efectivo contado por encima de 500.000.000 — mostrar el rechazo por
   el tope nuevo.
6. Mostrar que la caja ya no se puede reabrir, pero se puede abrir una nueva el mismo día (con
   "Sí, abrir caja" en su propio cartel de confirmación, nuevo).
7. Mostrar que cobros y desembolsos quedan bloqueados sin caja abierta.
