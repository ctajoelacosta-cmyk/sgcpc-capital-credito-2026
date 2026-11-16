# RF17 — Comprobante de desembolso antes de entregar el efectivo

**Estado: cerrado y probado en pantalla completo el 11/10/2026 — tag `rf17-probado`.** El texto
se cumple sin excepción: CC-2026-00001 se desembolsó de punta a punta, con el comprobante
DES-2026-000001 emitido e impreso antes de confirmar, y reimpreso después con la marca
"REIMPRESIÓN". Quedan 5 mejoras menores (sección 10, a-e, algunas compartidas con RF18), que NO
se hacen en esta vuelta.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF17 (módulo Desembolso y caja):

> El sistema debe emitir el comprobante de desembolso para la firma del cliente antes de
> confirmar la entrega del efectivo, y registrar la salida en la caja.

RF17 es el último paso de la misma ficha que RF16 (`/desembolso/{idPrestamo}`) — continúa
exactamente donde terminan las 4 condiciones.

## 2. Cómo funciona

En `desembolso/ficha.jsp`, debajo de "Condiciones para desembolsar" (RF16), dos cards:

1. **"Comprobante de desembolso"**: un enlace "Imprimir comprobante para firma" que abre el PDF
   en una pestaña nueva (`GET /desembolso/{id}/comprobante`). **Esta card se muestra siempre**,
   sin condición — no depende de que las 4 condiciones de RF16 estén cumplidas (ver hallazgo en
   la sección 6a).
2. **"Confirmar desembolso"** (solo si `puedeDesembolsarDesembolso`, las 4 de RF16): un
   formulario con una casilla obligatoria ("El cliente firmó el comprobante, verifiqué su
   cédula y le entregué [monto] en efectivo.") deshabilitada hasta haber impreso el comprobante
   al menos una vez, y el botón "Confirmar desembolso", deshabilitado hasta tildar la casilla.

`ServicioDesembolso.generarComprobantePreDesembolso` asigna el número `DES-AAAA-NNNNNN` la
PRIMERA vez que se imprime (con el préstamo todavía `PENDIENTE_DESEMBOLSO`) y lo reutiliza
siempre después — `confirmarDesembolso` (RF16) EXIGE que ese número ya exista, lanzando "Imprima
el comprobante de desembolso (para firma) antes de confirmar." si no.

## 3. Auditoría

| Evento | Entidad | Cuándo |
|---|---|---|
| `IMPRIMIR_COMPROBANTE_DESEMBOLSO` | PRESTAMO | Primera impresión (asigna el número) |
| `REIMPRIMIR_COMPROBANTE_DESEMBOLSO` | PRESTAMO | Cualquier impresión posterior (antes o después de confirmar) |
| `DESEMBOLSAR` | PRESTAMO | Se confirmó el desembolso (préstamo → `ACTIVO`, movimiento de caja insertado) |

## 4. Dónde está

- `servicios/ServicioDesembolso.java` — `generarComprobantePreDesembolso`,
  `obtenerPdfComprobanteDesembolso`, `construirMarcaReimpresion`, `registrarImpresionComprobante`,
  `generarPdfComprobante`, `confirmarDesembolso` (la parte final, después de las 4 condiciones
  de RF16 — ver `RF16_condiciones_desembolso.md`).
- `controladores/ControladorDesembolsoServlet.java` (`GET /desembolso/{id}/comprobante` — sirve
  tanto la impresión "para firma" como la reimpresión, según el estado del préstamo;
  `POST /desembolso/{id}/confirmar`).
- `webapp/WEB-INF/vistas/desembolso/ficha.jsp`.
- `src/main/resources/reportes/comprobante_desembolso.jrxml`.
- Tabla `desembolsos` (`numero_comprobante`, vía `prestamos.numero_comprobante_desembolso`),
  numerador `DESEMBOLSO`.

## 5. Dependencias

- **Depende de**: RF16 (las 4 condiciones tienen que estar en verde para que aparezca
  "Confirmar desembolso", aunque "Imprimir" no depende de nada — hallazgo 6a).
- **Lo afecta un cambio en**: `ServicioParametros` (datos de la empresa en el letterhead, igual
  que los PDF de RF12); el motor financiero (cuota 1.ª/última con IVA, mismo criterio que RF12).
- **A quién afecta un cambio acá**: RF18 (cada desembolso confirmado es un movimiento más de
  caja, con su comprobante reimprimible desde ahí); el arqueo de cierre (RF15, lista los
  desembolsos de la sesión).

## 6. Análisis

### 11/10/2026 — revisión completa de RF17 contra el texto firmado (pedido de Joel, solo lectura)

**Método**: lectura completa de la parte de comprobante de `ServicioDesembolso.java`,
`ControladorDesembolsoServlet.java`, `desembolso/ficha.jsp`, `comprobante_desembolso.jrxml`, y
`ServicioAutorizacion.construirMatriz` para el permiso `DESEMBOLSAR`.

### a) ¿Se controla también en el servidor?

**Sí, dos veces, independientes de la pantalla**:
1. El botón "Imprimir comprobante para firma" (card "Comprobante de desembolso") se muestra
   **siempre**, sin ninguna condición — **hallazgo, ya señalado por Joel en la prueba de
   pantalla de RF16**: se puede imprimir (y hacer firmar al cliente) un comprobante aunque
   falte, por ejemplo, el saldo en caja. El riesgo real que señaló Joel: el cliente firma que
   recibió el efectivo antes de que de verdad haya plata para dárselo. Confirmado en el código:
   la card del botón de imprimir vive FUERA del `<% if (puedeDesembolsarDesembolso) %>` que
   protege la de confirmar (`desembolso/ficha.jsp:143` vs `:160`).
2. **Confirmar sin haber impreso** (forzando un `POST` directo a `/desembolso/{id}/confirmar`
   sin pasar por "Imprimir"): `confirmarDesembolso` revisa
   `prestamo.getNumeroComprobanteDesembolso() == null` y rechaza con "Imprima el comprobante de
   desembolso (para firma) antes de confirmar." — no hay forma de confirmar sin que el número ya
   exista, sin importar qué mandó el formulario.
3. **Confirmar sin tildar la casilla** (forzando `confirmoEntregaEfectivo=false` o sin el
   parámetro): rechaza con "Debe verificar la cédula del cliente y confirmar que le entregó el
   efectivo." — la casilla deshabilitada en el HTML es solo una ayuda visual, el valor real que
   importa es el que lee el servidor del parámetro del formulario.

### b) Qué lleva el comprobante

Según `generarPdfComprobante` (`ServicioDesembolso.java:341-391`) y
`comprobante_desembolso.jrxml`:

| Dato | Presente |
|---|---|
| Número propio | Sí — `DES-AAAA-NNNNNN` (6 dígitos), **distinto** del `identificador_operacion` (`CC-AAAA-NNNNN`, RF12) — asignado la primera vez que se imprime, nunca cambia |
| Cliente (nombre, C.I., domicilio) | Sí |
| Monto en números y letras | Sí — `MONTO_NETO_LETRAS` en MAYÚSCULAS, mismo conversor que RF12 |
| Fecha y hora | Sí — de la operación real (al confirmar) o del momento de la impresión "para firma" |
| Cajera | Sí — nombre de quien emite |
| Firma | Sí — del cliente (con aclaración y C.I.) y de la cajera (según el comentario del código, línea 330-339) |
| Referencias | N.º de operación, de contrato y de pagaré (RF12); plazo, cuota 1.ª/última con IVA, TEA, CFT, primer vencimiento |
| Deducciones | "0 Gs. (sin comisiones ni gastos)" — explícito, RN-17 |
| Leyenda legal | "documento interno — no válido como comprobante fiscal" (no es una factura: el capital no es una venta, el IVA de los intereses ya se factura en cada cuota) |

**Dos ejemplares en el mismo PDF**: página 1 "ORIGINAL – Empresa", página 2 "COPIA – Cliente"
(`comprobante_desembolso.jrxml:60,263`) — esto es independiente de la reimpresión; es la forma
en que SIEMPRE se imprime (dos páginas, un comprobante).

**¿Se puede reimprimir?** Sí — desde la ficha del préstamo ya desembolsado, o desde `/caja`
("Reimprimir" en la fila del movimiento `DESEMBOLSO`, ver RF18), o desde el arqueo. Nunca se
cachea un PDF viejo: cada reimpresión se regenera al vuelo con los datos actuales.

**¿La reimpresión dice "COPIA" o "REIMPRESIÓN"?** **"REIMPRESIÓN"** — un texto
`REIMPRESIÓN — dd/mm/aaaa hh:mm — Nombre Apellido` (`construirMarcaReimpresion`) se agrega en
las DOS páginas (ORIGINAL y COPIA) cuando no es la primera impresión. "ORIGINAL"/"COPIA" es un
concepto distinto (los dos ejemplares de cualquier impresión, sea la primera o una reimpresión).

### c) Qué cambia al confirmar

- **Estado del préstamo**: `PENDIENTE_DESEMBOLSO` → `ACTIVO`, con `fecha_desembolso` = hoy
  (`PrestamoDAO.marcarDesembolsado`).
- **Movimiento de caja**: se inserta un `MovimientoCaja` tipo `DESEMBOLSO`, signo −1, por el
  monto neto (= monto aprobado, sin deducciones) — esto es, literalmente, "registrar la salida
  en la caja" del texto de RF17.
- **Fila en `desembolsos`**: con el número de comprobante (reutilizado, nunca uno nuevo), el
  snapshot del monto aprobado, quién lo desembolsó, la sesión de caja.
- **Auditoría**: `DESEMBOLSAR` sobre PRESTAMO, con el monto y la operación.
- **Qué ve después la cajera**: vuelve a su propia bandeja `/desembolso` (corrección de Joel —
  antes redirigía a `/formalizacion/{id}`, una ruta a la que no tiene acceso y le daba 403
  justo después del éxito), con un cartel de éxito que incluye un botón "Imprimir comprobante"
  (mismo PDF, ya marcado como reimpresión si se vuelve a pedir).
- **Qué ve después el oficial**: no se investigó en esta vuelta — el oficial no tiene acceso a
  `/desembolso` (`Permiso.DESEMBOLSAR` es `NINGUNO` para su perfil, 403 directo); si existe
  alguna otra pantalla donde vea que el préstamo ya pasó a `ACTIVO`, pertenece al seguimiento de
  cartera, fuera del alcance de RF17.

### d) Doble confirmación

Mismo mecanismo ya descrito en `RF16_condiciones_desembolso.md` §6h, con un detalle adicional
confirmado en esta lectura: el `catch (SQLException error)` de `confirmarDesembolso`
(`ServicioDesembolso.java:199-208`) revisa específicamente `error.getSQLState().equals("23505")`
(violación de unicidad en PostgreSQL) y, si es así, **trata la carrera como éxito idempotente**
— devuelve el préstamo igual, sin error. Esto cubre tanto el doble clic con la MISMA clave
(`identificador_confirmacion` repetida) como la carrera real de dos pestañas con DOS claves
distintas que chocan contra el índice único por préstamo
(`ux_desembolsos_un_confirmado_por_prestamo`, RF16 §6h): en los dos casos, quien pierde la
carrera ve el mismo cartel de éxito que quien la ganó, nunca un error — financieramente
correcto (nunca hay un desembolso duplicado), aunque la segunda cajera no tenga forma de saber,
por el mensaje, que en realidad fue la primera petición la que quedó registrada.

**CSRF**: el formulario de "Confirmar desembolso" incluye el token (`plantilla/csrf.jsp`), sin
nada específico de esta pantalla.

## 7. Decisiones

No se encontraron hallazgos que exijan programar algo — RF17 cumple el texto firmado tal cual
ya estaba implementado. Confirmado en pantalla el 11/10/2026 (sección 9). Quedan 5 mejoras
menores (sección 10, a-e), anotadas para una próxima vuelta.

### Quién puede reimprimir el comprobante, y desde dónde (B94, 30/10/2026)

La pregunta la contesta **el permiso, no el perfil**. La ruta del PDF `/desembolso/{id}/comprobante` no se resuelve por el prefijo `/desembolso` sino por la forma de sus segmentos, **antes** de la regla general (`FiltroAutorizacion.permisoDeDocumento`), y pide `VER_COMPROBANTES_COBRANZA` (D-146): **gerente y cajera en CONSULTA, administrador TOTAL, oficial NINGUNO**. Por eso las dos pantallas que ofrecen el botón preguntan por ese permiso:

- **Desembolso → ficha del préstamo** (`desembolso/ficha.jsp`, botón *"Reimprimir comprobante"*): solo la cajera llega, porque entrar a `/desembolso` pide `DESEMBOLSAR`.
- **Reportes → Estado de cuenta → ficha** (`reportes/estado_cuenta_ficha.jsp`, botón *"Comprobante de desembolso"*): lo ve cualquiera que tenga `VER_COMPROBANTES_COBRANZA` y el préstamo ya esté desembolsado. **Hasta el 30/10/2026 esa condición nombraba el perfil `CAJERO` escrito a mano**, así que el gerente tenía el permiso de la ruta y ningún botón: era su único camino y estaba cerrado.

Lo que **no** cambió: `/desembolso` y `/desembolso/{id}/confirmar` siguen siendo solo de la cajera, y la impresión *"para firma"* (la que graba el número de comprobante y habilita *"Confirmar desembolso"*) no es alcanzable desde el Estado de cuenta, porque ese botón exige que el préstamo ya esté desembolsado. Cada reimpresión se audita igual (D-94: el comprobante **no** se guarda en `archivos_binarios`, se regenera en cada impresión).

## 7b. Las fechas del comprobante (29/10/2026)

- **"Primer vencimiento"**: la fecha **EFECTIVA** de la cuota 1 — la que de verdad se va a cobrar,
  ya corrida al siguiente día hábil. Hasta el 29/10/2026 mostraba la **elegida** al formalizar
  (**C12**), que es la que guarda `prestamos.primera_fecha_vencimiento`; el contrato y el plan que
  el cliente firma ya mostraban la efectiva, así que el comprobante los contradecía cuando la
  elegida caía en sábado, domingo o feriado. El cálculo tiene un solo dueño:
  `ServicioFormalizacion.primeraFechaVencimientoEfectiva`.
- **"Asunción, {fecha y hora}" y el pie "Emitido por…"**: los dos salen del **mismo** dato, la
  fecha/hora de la **operación**, nunca del momento de imprimir. Con dos orígenes según el camino:
  la impresión *"para firma"* (antes de confirmar) usa `Reloj.ahora()`, porque todavía no existe
  la operación; la **reimpresión** posterior usa `desembolso.getFechaHoraDesembolso()`, la hora en
  que la cajera **confirmó** el desembolso. Por eso el papel firmado (impreso antes) y la
  reimpresión pueden tener minutos distintos: no son dos horas de impresión, son el "antes" y el
  "momento" de la misma operación.
- **La hora de la reimpresión** va aparte, en la marca *"Reimpreso por … el …"*
  (`construirMarcaReimpresion`), que sí usa `Reloj.ahora()`.
- **Este documento NO se guarda como PDF** (a diferencia de recibos, facturas y constancias, que
  viven en `archivos_binarios` y nunca se regeneran, D-94): se **regenera en cada impresión** y lo
  que queda registrado es la impresión, en auditoría
  (`IMPRIMIR_COMPROBANTE_DESEMBOLSO` / `REIMPRIMIR_COMPROBANTE_DESEMBOLSO`). Su estabilidad viene
  de que todos sus datos se leen de la operación ya guardada. **Consecuencia de C12, decidida por Joel el
  29/10/2026**: una reimpresión de un desembolso anterior a esa fecha muestra ahora la fecha
  efectiva, mientras que el papel que el cliente firmó tiene la elegida — **está bien así**, la
  reimpresión lleva además la marca *"Reimpreso por … el …"* que la distingue del original.
- **Verificado con Joel el 29/10/2026**: en su prueba, la reimpresión cayó en el mismo minuto que
  la confirmación del desembolso (16:42), por eso las dos horas coincidían. No hay nada que
  corregir: el documento fecha la operación, y la hora de la reimpresión vive en su marca.

| 29/10/2026 | **B58**: los ejemplares pasan a `ORIGINAL — ARCHIVO CAPITAL CRÉDITO` / `COPIA — CLIENTE`, el mismo formato `TIPO — DESTINATARIO` que la factura y el recibo. **El destinatario no cambia**: acá el original lo firma el cliente y lo archiva la empresa, porque es el acuse de recibo del efectivo. Hubo que ensanchar la celda de 101 a 201 px (el texto nuevo salía recortado). Las dos páginas y todo lo demás, igual. **Pendiente de verse en pantalla** (visto en la muestra) | *(este commit)* | — |

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 11/10/2026 | RF14: la confirmación del desembolso (`confirmarDesembolso`) queda protegida contra la carrera de dos pestañas con la anulación del préstamo — si pierde la carrera, rollback completo, SIN comprobante emitido ni movimiento de caja, con advertencia "Este préstamo ya fue anulado por... el..." — ver [RF14_anular_prestamo.md](RF14_anular_prestamo.md) §6f | *(pendiente)* | — |
| 11/10/2026 | Consolidación RF08-RF19 (Lote 1): "Emitido por" muestra la fecha de la operación real, no la de la reimpresión (A.3); "Reimpreso por..." separado; domicilio sin referencia (B.15); impresión gateada por las 4 condiciones de RF16 (A.5, ver `RF16_condiciones_desembolso.md`) | *(pendiente)* | — |
| 11/10/2026 | Probado en pantalla completo: CC-2026-00001 desembolsado de punta a punta (comprobante DES-2026-000001, confirmación, reimpresión con "REIMPRESIÓN") | *(pendiente)* | `rf17-probado` |
| 11/10/2026 | Análisis completo contra el texto firmado, sin cambios de código | *(pendiente)* | — |

| 29/10/2026 | **C12**: "Primer vencimiento" pasa a ser la fecha EFECTIVA de la cuota 1 (la misma que el contrato y el plan), con el cálculo en un solo dueño. Una línea, sin tocar el `.jrxml`. **Probado en pantalla el mismo día**: `DES-2026-000004` de `CC-2026-00005` mostró 30/11/2026 con la elegida en sábado 28/11. `mvn test` 841/841 | *(este commit)* | — |

| 30/10/2026 | **B94**: el botón **"Comprobante de desembolso"** de la ficha de Estado de cuenta pasa a preguntar por el **permiso que exige el filtro** (`VER_COMPROBANTES_COBRANZA`) en vez de nombrar el perfil `CAJERO` a mano. El gerente tenía ese permiso desde D-146 —para controlar la rendición— pero **ninguna pantalla le ofrecía el botón**, así que no tenía forma de reimprimir un comprobante. Ahora lo ven gerente, cajera y administrador; el oficial no. **No abre ninguna otra puerta** (test en `FiltroAutorizacionTest`): `/desembolso` sigue pidiendo `DESEMBOLSAR`, que el gerente no tiene, y la impresión *"para firma"* queda fuera porque el botón exige que el préstamo ya esté desembolsado. Con esta reimpresión quedó además **visto en pantalla** el texto de ejemplar de **B58** ([RF23_recibo_factura.md](RF23_recibo_factura.md)). **Probado en pantalla el mismo día 07:10** (gerente Ramón Benítez, `DES-2026-000004`). `mvn test` 858/858 | `b998e4d` | — |

| 30/10/2026 (paso 15 b) | **Desembolso real de `CC-2026-00004`** (1.000.000 Gs., cajera Sofía, 19:28-19:31), con las 4 condiciones en verde recién después de que el gerente cargara los **3 documentos firmados** y **entregara el efectivo** a la caja del día (que había abierto en 0): comprobante **`DES-2026-000005`** impreso para firma a las 19:28 y desembolso confirmado a las **19:31:46**. **Verificación de las fechas del comprobante (pedido de Joel)**: la impresión *"para firma"* usa `Reloj.ahora()` (`ServicioDesembolso:314`) porque **todavía no existe el desembolso**, y la **reimpresión** usa `desembolso.getFechaHoraDesembolso()` (`:344-345`), así que el encabezado y *"Emitido por"* muestran siempre **la fecha/hora de la operación real** y la reimpresión solo agrega su propia línea *"Reimpreso por … el …"* — **una reimpresión dentro de días seguirá diciendo 30/10/2026 19:31**. **No es un hallazgo**; la diferencia de 3 minutos entre el papel firmado (19:28) y el registro (19:31) es inherente a firmar antes de confirmar (D-130). **Lo que sí cambia en una reimpresión posterior** es el *"Primer vencimiento"*: el comprobante **no se guarda**, se regenera (D-94), y este préstamo quedó con las fechas corridas −31 días para el paso 15, así que mostrará **30/10/2026** en vez del 30/11/2026 que dice el papel firmado — anotado en `FASE2_PLAN.md` | `48501b6` | — |

## 9. Pruebas realizadas en pantalla

**11/10/2026 (Joel), después del Clean and Build, cajera Sofía Martínez, con CC-2026-00001:**
- **Comprobante DES-2026-000001** (ORIGINAL – Empresa / COPIA – Cliente): datos de Mirtha,
  referencia a la operación/contrato/pagaré CC-2026-00001, monto 5.000.000 en números y
  "CINCO MILLONES DE GUARANÍES" en letras (confirma que la corrección de la regla "de" del
  conversor, ya aprobada para RF11/RF12, también se refleja acá vía
  `ConversorNumerosALetras.comoLetrasConGuaranies`), condiciones del préstamo, "Recibí
  conforme", firmas del cliente y de la cajera — confirma la sección 6b completa.
- **Casilla habilitada solo después de imprimir**: "✓ Comprobante impreso" — confirma la
  sección 2/6a (el botón de imprimir se puede usar siempre, pero la casilla de confirmar sigue
  deshabilitada hasta la primera impresión).
- **Confirmación**: "Desembolso de CC-2026-00001 confirmado (5.000.000 Gs.)" con los botones
  "Aceptar" e "Imprimir comprobante"; la bandeja de `/desembolso` quedó vacía (CC-2026-00001 ya
  no es `PENDIENTE_DESEMBOLSO`) — confirma la sección 6c.
- **Reimpresión**: "REIMPRESIÓN — 11/10/2026 17:09 — Sofía Martínez" en los DOS ejemplares, con
  la fecha de la operación (17:08) manteniéndose sin cambios — confirma la sección 6b
  (`construirMarcaReimpresion`) y, de paso, confirma el hallazgo nuevo de la sección 10c (la
  marca usa la hora de la reimpresión, no dice "emitido originalmente a las...").

**Resultado: RF17 queda probado completo.** 5 mejoras menores encontradas (sección 10, a-e,
algunas compartidas con RF18), NO corregidas en esta vuelta.

## 10. Pendientes y trabajo futuro

- **RF14, carrera anular vs. desembolsar (11/10/2026)**: implementada, sin reprueba en pantalla
  todavía — ver [RF14_anular_prestamo.md](RF14_anular_prestamo.md) §10.

Mejoras menores encontradas en la prueba de pantalla del 11/10/2026 — **NO corregidas en esta
vuelta**:

a) Confirmación con texto explícito (`data-confirmar-boton`, mismo mecanismo que
   RF09/RF10/RF11/RF15): "Sí, desembolsar" (hoy usa el "Sí" genérico). **Implementada el
   11/10/2026** junto con RF19 — queda para la tanda de consolidación de pantalla.
   *(La mejora "b" de este pedido de Joel — mostrar el fondo inicial como primera fila de
   Movimientos — es de RF18, no de RF17: ver `RF18_movimientos_caja.md` §10.)*
c) ~~En la reimpresión, "Emitido por ... [hora de la reimpresión]" puede confundir~~ —
   **corregido el 11/10/2026** (consolidación RF08-RF19, Lote 1, A.3): el pie ("Emitido por")
   ahora siempre muestra la fecha/hora de la operación REAL (`fechaHoraOperacion`, antes se
   pisaba con `Reloj.ahora()` en cada reimpresión); `construirMarcaReimpresion` se reescribió a
   "Reimpreso por [usuario] el [fecha/hora de ESTA reimpresión]." — las dos fechas quedan
   claramente separadas. Confirmado (sección 10 de este mismo análisis previo) que
   `ServicioCobranza` (recibo/factura/constancia/reversión) nunca tuvo este problema. Pendiente
   de reprueba en pantalla.
d) ~~La referencia de domicilio tipo "(casa blanca con portón verde)"~~ (mismo hallazgo del
   contrato y el pagaré, `RF12_documentos_pdf.md` §10c) — **corregido el 11/10/2026** (B.15):
   `ServicioClientes.domicilioSinReferencia` aplicado también en `ServicioDesembolso` para
   `CLIENTE_DOMICILIO`. Pendiente de reprueba en pantalla.
e) Espacio en blanco al final de la página 1 del comprobante — mismo tipo de ajuste de altura
   de banda ya anotado para el contrato y el plan de pagos (`RF12_documentos_pdf.md` §10b).
   **Lote 2** (C.19), no tocado todavía.
- Confirmar en pantalla el desembolso de CC-2026-00001 de punta a punta — **hecho** (sección 9).

## 11. Guion para la defensa

**Confirmado en pantalla el 11/10/2026 (sección 9), con CC-2026-00001.**

1. Con las 4 condiciones de RF16 en verde, imprimir el comprobante — mostrar el número
   `DES-AAAA-NNNNNN`, los dos ejemplares (ORIGINAL/COPIA) y que la casilla de confirmar se
   habilita.
2. Confirmar el desembolso — mostrar el préstamo pasando a `ACTIVO` y el movimiento `DESEMBOLSO`
   en la caja.
3. Reimprimir el comprobante desde la ficha ya desembolsada — mostrar la marca "REIMPRESIÓN"
   con fecha/hora/usuario en las dos páginas.
4. Mostrar el hallazgo de la sección 6a: con otra operación de prueba, imprimir el comprobante
   SIN que las 4 condiciones estén cumplidas (ej. sin caja abierta) — confirmar que la impresión
   igual sale, aunque "Confirmar desembolso" no aparezca.
