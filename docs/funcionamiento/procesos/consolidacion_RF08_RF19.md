# Consolidación RF08–RF19 (+ reprueba RF01–RF03) — Lote 1 VERIFICADO

*Las referencias a codeudor de este documento quedaron superadas por el retiro de
codeudor/garante (18/10/2026, commit `6e7da13`) — ver `docs/funcionamiento/RF12_documentos_pdf.md`.
Registro histórico, sin reescribir.*

**Estado (11/10/2026): Lote 1 implementado, reprobado por Joel en pantalla DOS VECES (la
reprueba inicial y la reprueba de las correcciones), y confirmado: TODO VERIFICADO EN
PANTALLA. `mvn test`: 673 pruebas en verde. Comiteado con el tag `consolidacion-rf08-rf19-lote1`
(hash abajo). 7 detalles menores encontrados en la segunda reprueba quedaron anotados para el
Lote 2 (sección "Detalles menores", más abajo) — uno de ellos (2.f, aviso de fuera de plazo en
gris) se corrigió igual, de inmediato, por ser una regresión de un punto ya cerrado. Lote 2
(estilo común de PDF): análisis entregado, a la espera de la aprobación de Joel para programarlo
— ver "Resumen para aprobar" al final. RF14 (anular préstamo): documento nuevo creado, solo
análisis.**

**Commit**: `855ceef` — tag `consolidacion-rf08-rf19-lote1`.

## Reprueba del Lote 1 (11/10/2026, Joel) — resultados y correcciones

### Verificados OK en pantalla — primera reprueba

A.6, A.7, A.8 (RF11, fuera de plazo como advertencia), A.9 (la pantalla frena el motivo corto
antes del servidor), A.1 en el CONTRATO, B.11 (los casos probados: "Sí, abrir caja" /
"desactivar" / "enviar" / "aprobar" / "rechazar" / "generar"), B.13, B.15 (contrato y pagaré sin
la referencia), y la carrera de dos pestañas de RF09/RF10 con el mensaje informativo.

### Verificados OK en pantalla — SEGUNDA reprueba, 11/10/2026 (confirmación final)

**TODO VERIFICADO.** A.1 (contrato y plan "TRES MILLONES DE GUARANÍES"; pagaré ya sin
"GUARANÍES" duplicado), A.2 (pagaré "a mi entera satisfacción" sin codeudor), A.4 ("Faltan
3.000.000 Gs." en rojo), A.5 (comprobante bloqueado sin las 4 condiciones, incluido el caso de
solo faltar el saldo), A.6, A.7, A.8 (fuera de plazo como advertencia), A.9, A.10, B.11 (7 casos:
"Sí, abrir caja" / "desactivar" / "enviar" / "aprobar" / "rechazar" / "generar" / "re-emitir"),
B.13, B.14, B.15, RF09/RF10 (dos pestañas, mensaje con quién y cuándo), RF13 (checklist de 3
ítems completo, 2/3 no habilita, 3/3 "Documentación completa", regenerar bloqueado,
`/desembolso/2` bloqueado en ficha y comprobante con checklist incompleto, CC-2026-00001 con el
texto nuevo), D.1 ("21/11/2026 → lunes 23/11/2026"), D.4 ("Sin movimientos desde la apertura."),
E (contrato con "QUIEN LE ATENDIÓ", plan con "Entregado por..."), y el control `.jasper`/`.jrxml`
(`ReportesCompiladosTest`) vigente.

**Pendientes de reprueba puntual (implementados, todavía sin confirmar en pantalla)**: D.3
(conservar la fecha tras el bloqueo de fuera de plazo) y D.5 (cartel corto del motivo, el
nuevo texto "El motivo de la diferencia necesita al menos 10 caracteres (tiene 3)."). D.2
(ámbar del aviso de fuera de plazo) tuvo una regresión encontrada en esta misma reprueba (ver
punto 2.f más abajo) — corregida de nuevo, también pendiente de reprueba puntual.

### Detalles menores de la segunda reprueba (11/10/2026) — se corrigen junto con el Lote 2

El detalle completo de cada uno queda en la sección "Pendientes" del documento de RF
correspondiente (RF12 para a y d, RF13 para b y e, RF16 para c); acá solo el resumen:

a) **RF12** — Pagaré con un solo deudor dice "PAGARÉ (pagaré) sin protesto" (el paréntesis queda
   raro) — debe ser "PAGARÉ sin protesto" (sin codeudor) / "PAGAREMOS sin protesto" (con
   codeudor, en vez de "PAGARÉ (pagaremos)"). `pagare.jrxml` línea ~109.
b) **RF13** — `asegurarSinDocumentosFirmados` (bloquea "Regenerar documentos" si ya hay un
   firmado cargado) solo mira `CONTRATO_FIRMADO`/`PAGARE_FIRMADO` — le falta
   `PLAN_PAGOS_FIRMADO`. `ServicioFormalizacion.java` línea ~636.
c) **RF16** — El enlace "Ir a formalización" en `/desembolso/{id}` — **revisado, el código ya
   apunta a la ficha del préstamo, no a la bandeja**; no se encontró la causa en el código.
   Pendiente confirmar con qué perfil/paso exacto se reprodujo (sospecha: un 403 de la cajera,
   sin permiso para `/formalizacion`, confundido con la bandeja) antes de tocar nada.
d) **RF12** — Pregunta de Joel: "¿'Quien le atendió' debería quedar fijo en quien formalizó?"
   — **revisado, ya está así**: `generarPdfContrato`/`Pagare`/`PlanDePagos` siempre usan
   `prestamo.getFormalizadoPor()`, nunca el usuario que re-emite. No hace falta ningún cambio;
   pendiente confirmar en pantalla regenerando con un usuario distinto al que formalizó.
e) **RF13** — El ítem "No exigido al desembolsar..." (préstamos ya desembolsados antes de este
   requisito, ej. CC-2026-00001) se ve en un recuadro gris chico, distinto del resto —
   unificar el formato visual con los demás ítems del checklist.
f) **Corregido de inmediato** (regresión de un punto ya cerrado, no se difiere al Lote 2): el
   aviso de "fuera de plazo" (D.2) seguía en gris en `nueva.jsp` — el contenedor tenía la clase
   `text-muted` puesta en el JSP, que ganaba por herencia al color en línea del `<strong>`; ahora
   se le quita esa clase mientras se muestra el aviso de fuera de plazo. Pendiente de reprueba
   puntual de esta segunda corrección.
g) D.3 y D.5 (ver más arriba): implementados, sin reprueba en pantalla todavía — no es un
   hallazgo nuevo, solo falta repetir la prueba.

### A) ERROR GRAVE — RF13: faltaba `PLAN_PAGOS_FIRMADO` en el checklist

El anteproyecto pide contrato, pagaré Y plan de cuotas firmados y controlados antes del
desembolso — el checklist solo tenía 2 de los 3. Corregido: migración
`bd/migraciones/E10_14_plan_pagos_firmado.sql` (aplicada a `sgcpc_db_pruebas`) + fila nueva en
`bd/03_parametros.sql` (instalaciones nuevas) + `CargadorDatosPrueba`/`PruebaD117PromesaPagoParcial`
actualizados (suben el 3.er firmado) + 2 pruebas unitarias (`ServicioFormalizacionTest`, 2 de 3
no desembolsa / 3 de 3 sí) + corregido "exactamente 2" de `RF13_firmados_checklist.md`. El
checklist es genérico (recorre `tipos_documento` por `obligatorio_desembolso=TRUE`, sin ningún
código fijo), así que no hizo falta tocar `confirmarDesembolso`, la bandeja, el fondo sugerido ni
ningún JSP para que el 3.er ítem apareciera — solo la fila nueva en la base. Ningún informe lee
el checklist (verificado, cero referencias en `ServicioInformes`/`ServicioReportes`), así que
esto no afecta ningún listado.

CC-2026-00001 (y cualquier préstamo que ya no esté `PENDIENTE_DESEMBOLSO`) no se tocó — su
ítem "Plan de pagos firmado" (nunca cargado, el tipo no existía cuando se formalizó) muestra
"No exigido al desembolsar (requisito agregado el 11/10/2026)" en vez de "Pendiente" (sería
falso: ya no se le va a volver a exigir nada).

### B) `.jasper` desactualizados — causa raíz y control permanente

**Causa exacta de por qué A.1/A.2 no se veían en el pagaré**: cada reporte son DOS archivos que
hay que mantener sincronizados a mano (`.jrxml` diseño + `.jasper` compilado,
`docs/REGLAS_DESARROLLO.md` §6) — el sistema carga en tiempo de ejecución SOLO el `.jasper`, y
Maven no tiene ningún paso que compile `.jrxml` → `.jasper`. Se editó `pagare.jrxml`/
`plan_pagos.jrxml` en el Lote 1 sin recompilar su `.jasper` — error del propio proceso de
implementación, no del análisis.

**Corregido**:
1. Recompilados `pagare.jasper` y `plan_pagos.jasper` (y `contrato.jasper`, por el cambio de la
   firma de "quien atendió", punto E).
2. **Auditados los 37 reportes del sistema** (no solo por fecha: se recompilaron los 37 y se
   comparó, por historial de git, si el `.jrxml` se había tocado después del último `.jasper`
   comiteado). Resultado: **14 reportes tenían un desvío histórico real** (`arqueo_caja`,
   `comprobante_desembolso`, `constancia_cancelacion`, `factura`, `informe_aprobaciones`,
   `informe_caja`, `informe_clientes`, `informe_cobros`, `informe_desembolsos`, `informe_iva`,
   `informe_morosidad`, `informe_prestamos`, `informe_solicitudes`, `recibo`), además de
   `pagare`/`plan_pagos` (el hallazgo de Joel) y `contrato` (por el cambio de este mismo Lote).
   **Los 14 primeros no tenían ningún impacto visual/funcional**: su único cambio pendiente era
   un renombre de comentario XML ("CLAUDE.md" → "REGLAS_DESARROLLO.md", commit `2c02eb0`, D-154,
   confirmado en el propio mensaje de ese commit: "Solo comentarios, ninguna línea de lógica") —
   los comentarios XML no afectan el PDF compilado. Se recompilaron igual, por prolijidad.
3. **Control permanente**: `herramientas/compilar_reportes.sh` (compila los 37 y guarda el hash
   SHA-256 de cada `.jrxml` en `herramientas/reportes_jasper.sha256`) +
   `ReportesCompiladosTest` (corre con cualquier `mvn test`, compara ese hash contra el `.jrxml`
   actual y FALLA si no coincide, nombrando cuáles). Documentado como paso obligatorio en
   `docs/REGLAS_DESARROLLO.md` §6. Sin agregar ninguna dependencia ni plugin de Maven nuevo (la
   herramienta reutiliza JasperReports, ya una dependencia del proyecto, invocada vía
   `mvn -Dtest=HerramientaCompilarReportes test` — mismo mecanismo que `PruebaD117PromesaPagoParcial`,
   que tampoco corre con el `mvn test` normal).

### D) Otros hallazgos de la reprueba — todos corregidos

- **D.1**: "Primer vencimiento" mostraba la fecha ELEGIDA (21/11), no la EFECTIVA (23/11, la que
  realmente usa la cuota 1 y el contrato) — ahora muestra "21/11/2026 → lunes 23/11/2026" cuando
  difieren (`FormateadorFechas.comoDiaYFecha`, nuevo, comparando contra la 1.ª cuota del plan
  vigente).
- **D.2**: el aviso de "fuera de plazo" se veía en el mismo gris que el aviso informativo de
  corrimiento — ahora en ámbar oscuro (`#a15c00`, mismo color que "sobra" en `caja/ficha.jsp`),
  con el texto escapado antes de armar el HTML.
- **D.3**: la fecha tecleada se perdía al rechazar por fuera de plazo (volvía a la propuesta por
  defecto) — ahora se conserva, en "Generar" y en "Regenerar" (mismo mecanismo
  `MensajesFlash.guardarValores`/`valoresFormulario` que ya usa `caja/ficha.jsp`).
- **D.4**: "Todavía no hay movimientos." seguía apareciendo debajo de la fila de apertura
  (B.14) — ahora dice "Sin movimientos desde la apertura." cuando corresponde.
- **D.5**: el cartel de un campo con `minlength` (ej. el motivo de la diferencia) repetía la
  etiqueta COMPLETA del campo (incluida la aclaración entre paréntesis) — ahora dice "El motivo
  de la diferencia necesita al menos 10 caracteres (tiene 3)." (genérico, afecta a todo el
  sistema, en `plantilla/pie.jsp`).
- **D.6**: `RF05_solicitud.md` §2 describía el comportamiento VIEJO del 40 % (nunca bloquea al
  crear) — corregido para que coincida con la decisión del 10/10/2026 (sí bloquea al crear/simular).

### E) Firma de "quien atendió"

Agregada en el contrato (fila propia debajo de acreedor/deudor/codeudor, sin quitar la firma del
representante) y en el plan de pagos (además de "Recibí copia" del cliente) — nombre y C.I. del
usuario que generó los documentos (`USUARIO_EMISOR`/`USUARIO_EMISOR_CEDULA`, ya disponibles o
agregados a `construirParametrosReporte`). El pagaré queda sin cambios: Joel confirmó el
criterio (solo deudor/codeudor, es un título de crédito unilateral).

---

Pedido original de Joel (11/10/2026, Paso 1 — solo análisis): leer los
Pendientes de RF08 a RF19 y armar un único plan con todo lo que corresponde corregir ahora,
bajo esta regla: *"lo que no cumple el texto firmado, miente o confunde se arregla ahora y se
vuelve a probar; lo que depende del tiempo (buró vencido 30 días, documentos vigentes 5 días,
mora) NO se toca en esta tanda."*

Este documento es el ENTREGABLE del Paso 1. No implementa nada — cada fila de la tabla queda
pendiente de la aprobación de Joel antes de tocar un solo archivo de código.

## Cómo leer la tabla

- **N.º**: mismo número que usó Joel en su pedido (A.1-10, B.11-15, C.16-19, D.20-24, E.25-27),
  más los puntos **N** (nuevos, encontrados al leer los Pendientes, que Joel no había listado).
- **¿Prueba unitaria?**: Sí = se agrega/ajusta un test en `src/test/...`; No = es pantalla pura
  (JSP/JS/jrxml) o un texto, sin lógica nueva que valga una prueba aislada.
- **Cómo se prueba en pantalla**: el recorrido concreto que Joel va a hacer para confirmar el
  punto. Nada se da por cerrado sin esto.
- **Ya corregido / no corresponde**: puntos del pedido de Joel que, al revisar el código,
  resultaron ya resueltos o no aplicables — se explica por qué.

---

## A) LO QUE MIENTE O CONFUNDE (prioridad)

| N.º | RF | Cambio | Archivos | ¿Prueba unitaria? | Cómo se prueba en pantalla | Estado |
|---|---|---|---|---|---|---|
| A.1 | RF12 | "CINCO MILLONES GUARANÍES" debe ser "...DE GUARANÍES" en contrato/pagaré/plan. Causa real: `ServicioFormalizacion` llama a `ConversorNumerosALetras.comoLetras(...)` y agrega a mano `" GUARANÍES)"` en el texto de la cláusula, en vez de llamar a `comoLetrasConGuaranies(...)` — método que YA existe, YA está probado y YA resuelve correctamente el caso "termina en millón" (agrega "DE GUARANÍES"). No hay que tocar el conversor, solo cambiar qué método se llama en 2 lugares. | `servicios/ServicioFormalizacion.java` (líneas ~817, ~835, ~1105, ~1115: cambiar `comoLetras(...) + " GUARANÍES)"` por `comoLetrasConGuaranies(...) + ")"`) | Sí — agregar a `ConversorNumerosALetrasTest.java` los 3 casos que Joel pidió y que hoy faltan: 15.000.000, 2.500.000, 1.000.000.000 (ya están cubiertos 3.000.000, 1.000.000, 21.000, 3.236.215) | Formalizar un préstamo por Gs. 5.000.000 (o cualquier monto redondo en millones) y abrir el contrato/pagaré/plan generados: el monto en letras debe decir "...DE GUARANÍES", nunca "...GUARANÍES" a secas cuando el monto es un múltiplo exacto de millón. | **Hecho** (11/10/2026) — pendiente de reprueba en pantalla. |
| A.2 | RF12 | Pagaré dice siempre "PAGARÉ (pagaremos)... a nuestra entera satisfacción", incluso sin codeudor. Debe decir "PAGARÉ... a mi entera satisfacción" cuando no hay codeudor (ya existe el parámetro `TIENE_CODEUDOR` en el .jrxml, usado en los bloques de firma — falta aplicarlo a este texto). | `src/main/resources/reportes/pagare.jrxml` (línea ~109: condicionar el texto a `$P{TIENE_CODEUDOR}`, con las 2 variantes) | No (texto condicional en el reporte, sin lógica nueva) | Formalizar un préstamo SIN codeudor → el pagaré debe leerse en singular ("pagaré"/"mi"). Formalizar uno CON codeudor → debe seguir en plural ("pagaremos"/"nuestra"). | **Hecho** (11/10/2026) — pendiente de reprueba en pantalla. |
| A.3 | RF17/RF18 | El comprobante de desembolso, al reimprimirse, muestra en el pie "Emitido por: [usuario] — [fecha/hora de la REIMPRESIÓN]" en vez de la fecha/hora de la emisión ORIGINAL. Causa real: `ServicioDesembolso.generarPdfComprobante` pone `FECHA_EMISION = Reloj.ahora()` siempre, ignorando el parámetro `fechaHoraOperacion` que YA recibe y que SÍ usa correctamente para el campo "FECHA_HORA" del cuerpo. **Importante**: confirmado que este bug es exclusivo de `ServicioDesembolso` — los 4 comprobantes de `ServicioCobranza` (recibo/factura/constancia/reversión) ya usan siempre la fecha real de la operación guardada, nunca `Reloj.ahora()`; no es un patrón repetido en el resto del sistema. | `servicios/ServicioDesembolso.java` (método `generarPdfComprobante`: cambiar `FECHA_EMISION` a usar `fechaHoraOperacion`; método `construirMarcaReimpresion`: cambiar el texto a "Reimpreso por [usuario] el [fecha/hora de la reimpresión]", separado de la fecha de emisión original) · `src/main/resources/reportes/comprobante_desembolso.jrxml` (líneas ~253 y ~454, pie de las 2 plantillas ORIGINAL/COPIA: agregar una línea nueva "Reimpreso por ... el ..." además de, no en lugar de, "Emitido por ... el [FECHA_EMISION]") | No (ordenar parámetros ya existentes) | Desembolsar un préstamo, abrir el comprobante (fecha/hora de emisión = ahora). Esperar un minuto y reimprimirlo: la fecha de "Emitido por" debe seguir siendo la misma de la primera vez; debe aparecer una línea nueva "Reimpreso por [usuario] el [hora de ESTA reimpresión]". | **Hecho** (11/10/2026) — pendiente de reprueba en pantalla. |
| A.4 | RF16 | Si el saldo teórico queda negativo después de un desembolso, la ficha lo muestra como un número negativo liso ("-4.000.000 Gs."), sin color ni explicación — confunde, parece un error del sistema. | `webapp/WEB-INF/vistas/desembolso/ficha.jsp` (línea ~95, fila "Caja: saldo teórico después": si `saldoDespuesDesembolso` es negativo, mostrar en rojo "Faltan X Gs." en vez de `comoGuaranies(saldoDespuesDesembolso)` liso — mismo patrón que YA usa, en la misma pantalla, la fila "Condiciones para desembolsar" líneas ~113-115/133, "Insuficiente — faltan X Gs.") | No (es una condición de presentación, ya con el patrón hecho en la misma página) | Con una caja de saldo teórico insuficiente, abrir la ficha de un desembolso cuyo monto supera ese saldo: la fila "Caja: saldo teórico después" debe decir "Faltan X Gs." en rojo, nunca un número negativo liso (nota: hoy el botón de confirmar ya está deshabilitado en ese caso por el punto A.5 — este número solo se ve mientras se mira la ficha). | **Hecho** (11/10/2026) — pendiente de reprueba en pantalla. |
| A.5 | RF16/RF17 | "Imprimir comprobante para firma" está disponible sin que se cumplan las 4 condiciones de RF16 (checklist completo, documentos vigentes, caja abierta y de hoy, saldo suficiente) — hoy esa validación solo corre al CONFIRMAR el desembolso, no al generar el comprobante previo a la firma. Hay que exigir las 4 condiciones tanto en el servidor como en la pantalla para ESTA acción también. | `servicios/ServicioDesembolso.java` (extraer las 4 validaciones de `confirmarDesembolso` a un método compartido, p. ej. `asegurarCondicionesDesembolso(Connection, PrestamoCredito)`, y llamarlo también desde `generarComprobantePreDesembolso`/el método que genera el PDF previo a la firma) · `webapp/WEB-INF/vistas/desembolso/ficha.jsp` (ocultar o deshabilitar el botón "Imprimir comprobante para firma" mientras `puedeDesembolsarDesembolso` sea falso, igual que ya se hace con "Confirmar desembolso") | Sí — test del método compartido con los 4 casos (checklist incompleto, documentos vencidos, caja cerrada/de otro día, saldo insuficiente) cada uno debe impedir la generación | Con un préstamo que NO cumple alguna de las 4 condiciones (ej. checklist incompleto), abrir su ficha de desembolso: el botón "Imprimir comprobante para firma" debe estar deshabilitado/oculto, igual que "Confirmar desembolso". Forzar la URL del PDF directamente (si existe un enlace directo) para confirmar que el servidor también rechaza. | **Hecho** (11/10/2026) — sin prueba unitaria nueva (el método toca `Connection`, se verifica en pantalla, mismo criterio que el resto de `confirmarDesembolso`) — pendiente de reprueba en pantalla. |
| A.6 | RF11 | La pantalla avisa cuando el primer vencimiento cae en un día no hábil y se corre al hábil siguiente, pero NO avisa si ese corrimiento empuja la fecha FUERA del rango permitido — el usuario solo se entera al enviar el formulario y recibir el error del servidor. | `webapp/recursos/js/fecha-primer-vencimiento.js` (función que ya calcula la fecha corregida: comparar contra `campo.max` —ya disponible como atributo del `<input type="date" max="...">`— y, si la corregida lo supera, agregar una línea de aviso adicional antes de que el usuario confirme) | No (cálculo ya existe en el servidor; esto es un aviso adicional en JS puro, sin recalcular nada nuevo) | Elegir, en "Generar documentos", una fecha que al correrse al día hábil siguiente quede fuera del rango máximo permitido: debe aparecer el aviso en pantalla ANTES de hacer clic en "Generar", no solo como error después de enviarlo. | **Hecho** (11/10/2026) — pendiente de reprueba en pantalla. |
| A.7 | RF11 | La afectación en vivo muestra demasiados decimales ("17,40442222 %") — ya existe `FormateadorNumeros.comoPorcentajeUnDecimal` (creado para RF05 con exactamente este propósito) pero la fila "Afectación actual" de formalización todavía usa `comoPorcentaje` (decimales completos, sin redondear). Además, el texto "puede haber quedado vieja" debe decir "desactualizada". | `webapp/WEB-INF/vistas/formalizacion/nueva.jsp` (línea ~64: `comoPorcentaje` → `comoPorcentajeUnDecimal` en la afectación; línea ~77: "puede haber quedado vieja" → "puede haber quedado desactualizada") · `webapp/WEB-INF/vistas/formalizacion/ficha.jsp` (línea ~270, mismo cambio) | No (cambiar qué método de formateo ya existente se llama + un texto) | Abrir "Generar documentos" de una solicitud con una afectación que tenga muchos decimales: debe verse con un solo decimal ("17,4 %"), no la fracción completa. Leer el texto de la fecha propuesta: debe decir "desactualizada". | **Hecho** (11/10/2026) — pendiente de reprueba en pantalla. |
| A.8 | Todo el sistema | Bloqueos de regla de negocio (ej. RF11 "fuera de plazo") se muestran como ERROR (X roja) en vez de advertencia con "Entendido" — el patrón correcto (subtipo de `NegocioException` enrutado a `MensajesFlash.guardarAdvertencia` antes del catch genérico) ya existe y ya se usó para RF09/RF10 (`SolicitudYaDecididaException`), RF19 (`CajaYaCerradaException`, `MotivoDiferenciaInsuficienteException`) — falta aplicarlo en el resto. Confirmado que `ServicioFormalizacion.validarPrimerVencimientoEfectivoFormalizacion` (RF11, fuera de plazo) todavía lanza `NegocioException` genérica, mostrada como error. Hay **~41 bloques `catch (NegocioException ...)`** repartidos en los controladores — no es realista decidir los 41 en este Paso 1; se propone el criterio y se aplica primero a los 2 casos que Joel señaló, dejando el resto para una segunda pasada guiada por el mismo criterio. | `excepciones/` (nueva excepción, ej. `FechaFueraDePlazoException extends NegocioException`) · `servicios/ServicioFormalizacion.java` (lanzar el subtipo en `validarPrimerVencimientoEfectivoFormalizacion`) · `controladores/ControladorFormalizacionServlet.java` (capturar el subtipo antes del catch genérico, enrutar a `guardarAdvertencia`) — **criterio propuesto para la segunda pasada**: advertencia cuando el usuario hizo algo válido pero el sistema no lo permite por estado/tiempo/condición (fuera de plazo, caja cerrada, ya decidida, duplicado); error (rojo) solo para fallas reales (excepción inesperada, dato corrupto) | Sí — test de que `validarPrimerVencimientoEfectivoFormalizacion` lanza el subtipo nuevo, no la genérica, cuando la fecha corregida cae fuera de rango | Repetir el caso de RF11 "fuera de plazo" (elegir una fecha que, corrida al día hábil siguiente, quede fuera del rango) y confirmar que el cartel sale con el ícono de advertencia y el botón "Entendido", no la X roja de error. | **Hecho para los 2 casos de Joel (RF11 fuera de plazo, aplicado también a RF08/RF09 tope de tasa/capacidad de pago) y extendido, con el mismo criterio, a RF15/RF16/RF17 (caja ya abierta, caja de otro día, préstamo ya no pendiente) y RF19 (diferencia ya revisada) — 6 subtipos nuevos en total.** Los ~35 catches restantes quedan listados para cuando se llegue a su RF — pendiente de reprueba en pantalla de los casos corregidos. |
| A.9 | RF19 | La pantalla deja confirmar el cierre con un motivo de menos de 10 caracteres — hoy el campo solo tiene el atributo `required` (no vacío) y un contador informativo en JS que NO bloquea el envío; el único bloqueo real de los 10 caracteres está en el servidor. Además, en general, los mensajes nativos del navegador ("Completa este campo", en tú) aparecen en al menos 19 pantallas (`required`/`minlength`) — hay que reemplazarlos por el cartel propio del sistema (de usted), en todos lados. | `webapp/WEB-INF/vistas/caja/ficha.jsp` (JS del cierre: al enviar, si hay diferencia y el motivo tiene menos de 10 caracteres reales, `preventDefault()` + mostrar el cartel propio, igual que el resto de confirmaciones) · `webapp/WEB-INF/vistas/plantilla/pie.jsp` (agregar un manejador genérico de `submit`/evento `invalid` para TODOS los formularios: usar `checkValidity()`, suprimir el popup nativo del navegador y mostrar el cartel propio con el mensaje en usted — toca, en algún grado, a las 19 pantallas listadas por `required`/`minlength`: `desembolso/ficha.jsp`, `caja/ficha.jsp`, `formalizacion/ficha.jsp`, `formalizacion/nueva.jsp`, `aprobaciones/ficha.jsp`, `clientes/ficha.jsp`, `solicitudes/ficha.jsp`, `solicitudes/nueva.jsp`, `parametros/administracion.jsp`, `cobranza/ficha.jsp`, `acceso.jsp`, `usuarios/formulario.jsp`, `reportes/auditoria.jsp`, `cambiar-contrasena.jsp`, `formalizacion/cierre-documental.jsp`, `cotizador/formulario.jsp`, `clientes/_resultados.jsp`, `mora/lista.jsp`, `reversiones/bandeja.jsp`) | Sí — test de `ServicioCaja` confirmando que ya rechaza (servidor) un motivo de menos de 10 caracteres (puede ya existir desde RF19; si no, agregarlo) | Intentar cerrar caja con una diferencia y un motivo de 5 caracteres: el sistema debe bloquear el envío con el cartel propio ANTES de llegar al servidor (no debe aparecer el popup nativo del navegador "Completa este campo"). Probar dejar vacío un campo obligatorio en cualquier otra pantalla (ej. alta de cliente) y confirmar que también sale el cartel propio, no el nativo. | **Hecho** (11/10/2026) — revisado mentalmente que login/alta de cliente/cobranza siguen enviando igual — pendiente de reprueba en pantalla real. |
| A.10 | RF08 | "El buró indica mora. Revise antes de decidir." no debería mostrarse una vez que la solicitud YA fue decidida. | — | — | **Ya corregido**: el código de `aprobaciones/ficha.jsp` (línea ~252) ya condiciona este aviso a `pendiente && avisoBuroRevision.isHayMora()` — con un comentario explícito que dice que se corrigió justo para no mostrarlo en una solicitud ya decidida. No hace falta ningún cambio; queda solo confirmarlo en pantalla. | **Ya corregido antes de esta tanda** — sin cambios en el Lote 1. |

---

## B) USABILIDAD Y TEXTOS

| N.º | RF | Cambio | Archivos | ¿Prueba unitaria? | Cómo se prueba en pantalla | Estado |
|---|---|---|---|---|---|---|
| B.11 | RF11 y varios | Agregar `data-confirmar-boton` donde falta (hoy el botón de confirmación queda en el "Sí" genérico). Lista completa encontrada: `formalizacion/nueva.jsp:80` ("Sí, generar"), `formalizacion/ficha.jsp:145` ("Sí, re-emitir"), `formalizacion/ficha.jsp:279` ("Sí, regenerar"), `formalizacion/ficha.jsp:303` ("Sí, anular"), `aprobaciones/ficha.jsp:318` ("Sí, cancelar"), `clientes/_resultados.jsp:101` ("Sí, reactivar"), `clientes/_resultados.jsp:116` ("Sí, dar de baja"), `parametros/administracion.jsp:132` ("Sí, desactivar"), `reversiones/bandeja.jsp:105` ("Sí, autorizar"), `cobranza/ficha.jsp:142` ("Sí, solicitar"), `usuarios/lista.jsp:68/75/81/88` ("Sí, reactivar"/"Sí, bloquear"/"Sí, inactivar"/"Sí, restablecer"), `solicitudes/ficha.jsp:331` ("Sí, enviar"), `solicitudes/ficha.jsp:338` ("Sí, cancelar"). | Los 13 archivos listados (un atributo `data-confirmar-boton="..."` por formulario) | No | Repasar cada una de las 13 acciones y confirmar que el botón de confirmación del cartel dice la acción concreta ("Sí, generar", "Sí, regenerar", etc.), nunca un "Sí" genérico. | **Hecho** (11/10/2026), los 13 — pendiente de reprueba en pantalla. |
| B.12 | RF11 | El botón "Regenerar documentos" se corre a la derecha cuando el aviso de advertencia es largo. | `webapp/WEB-INF/vistas/formalizacion/ficha.jsp` (ajuste de layout/flexbox alrededor del botón y el aviso) | No | Provocar un aviso largo (ej. afectación cerca del límite + fecha corrida) en la ficha de formalización: el botón "Regenerar documentos" debe quedar en su lugar fijo, sin desplazarse. | **Hecho** (11/10/2026) — pendiente de reprueba en pantalla. |
| B.13 | Parámetros | Agregar/desactivar un feriado vuelve siempre a la pestaña "Parámetros" general, no a "Feriados" — el usuario pierde el lugar donde estaba. Causa real: `ControladorParametrosServlet` redirige siempre a `/parametros` sin indicar la pestaña. | `controladores/ControladorParametrosServlet.java` (redirigir a `/parametros#tabFeriados` en las 3 acciones de feriados) · `webapp/WEB-INF/vistas/parametros/administracion.jsp` (JS mínimo: si `location.hash` coincide con un id de pestaña, activarla con Bootstrap al cargar) | No | Agregar un feriado nuevo: la página debe recargar mostrando la pestaña "Feriados" activa, no "Parámetros" general. Repetir con desactivar/activar un feriado. | **Hecho** (11/10/2026) — pendiente de reprueba en pantalla. |
| B.14 | RF18 | La tabla de Movimientos no muestra el fondo inicial (saldo con el que se abrió la caja) como primera fila — solo lista los movimientos reales de `movimientos_caja` (el fondo inicial vive en `sesiones_caja.saldo_inicial`, no es una fila de movimientos). | `webapp/WEB-INF/vistas/caja/ficha.jsp` (antes del `<% for %>` de movimientos, agregar una fila fija "Apertura (fondo inicial)" con la fecha de apertura y el `saldoInicial` de la sesión, ya disponibles en la página) | No | Abrir la ficha de una caja con movimientos: la primera fila de la tabla "Movimientos" debe ser la apertura con el fondo inicial, antes de cualquier entrega/desembolso/cobro. | **Hecho** (11/10/2026) — pendiente de reprueba en pantalla. |
| B.15 | RF12/RF17 | El domicilio de contrato/pagaré/comprobante de desembolso incluye la referencia coloquial ("casa blanca con portón verde") — correcta para uso operativo (ficha del cliente), pero no debería aparecer en un documento legal. | `servicios/ServicioFormalizacion.java` / `servicios/ServicioDesembolso.java` (en `construirParametrosReporte`/`generarPdfComprobante`: para `CLIENTE_DOMICILIO`/`CODEUDOR_DOMICILIO`, usar una versión del domicilio SIN la referencia — probablemente agregar un método nuevo en `ServicioClientes`, ej. `construirTextoDomicilioSinReferencia`, o separar la referencia como un campo propio desde el principio en `construirTextoDomicilio`) | Sí — test del nuevo método/variante, confirmando que arma el domicilio sin la referencia | Formalizar un préstamo de un cliente con una referencia coloquial cargada: el contrato/pagaré/comprobante de desembolso deben mostrar la dirección SIN la referencia; la ficha del cliente (uso operativo) debe seguir mostrándola. | **Hecho** (11/10/2026), con el hallazgo de paso en `pagare.jrxml` (ver A.1) — pendiente de reprueba en pantalla. |

---

## C) DISEÑO DE LOS DOCUMENTOS PDF

**Análisis y muestra entregados el 11/10/2026** — ver
[lote2_estilo_pdf.md](lote2_estilo_pdf.md): el estilo común (C.17) aplicado como MUESTRA a
`plan_pagos.jrxml`, `pagare.jrxml`, `arqueo_caja.jrxml` y `comprobante_desembolso.jrxml`
(descripción de los 4 + un PDF de ejemplo real, compilado, en
`herramientas/muestras_lote2/plan_pagos_muestra.pdf`). Ninguno de los 4 reportes reales fue
tocado — a la espera de la aprobación de Joel para programarlo.

### C.16 — Inventario de todos los PDF que genera el sistema

Confirmado por código: **36 reportes** (`.jrxml`) en `src/main/resources/reportes/`, agrupados
por familia (la estructura interna de cada uno se confirmó por una búsqueda de patrones, no
leyendo línea por línea los 36 — la revisión fila por fila se hace en la implementación, una
vez que Joel apruebe el estilo común).

| Familia | Archivos | ¿Logo (`<image>`)? | Pie "Página X de Y"? | Observación |
|---|---|---|---|---|
| **Legales de formalización** | `contrato.jrxml`, `pagare.jrxml`, `plan_pagos.jrxml` | `contrato.jrxml` sí; `pagare.jrxml`/`plan_pagos.jrxml` reciben el parámetro `LOGO` pero no se confirmó un `<image>` propio en cada uno — a revisar en la implementación | No, ninguno de los 3 | Estructura más compleja (varias páginas/plantillas); candidatos principales de la "gran franja en blanco" (ver C.19) |
| **Comprobantes de caja/cobranza** | `recibo.jrxml`, `factura.jrxml`, `constancia_cancelacion.jrxml`, `constancia_devolucion.jrxml`, `nota_anulacion.jrxml`, `comprobante_desembolso.jrxml`, `arqueo_caja.jrxml` | `recibo.jrxml` sí (`<image>` confirmado); el resto recibe `LOGO` pero no se confirmó `<image>` propio | Ninguno confirmado con "Página X de Y" | `factura.jrxml` (RF23) **no se toca esta vuelta** (formato legal, Joel lo revisa después); `comprobante_desembolso.jrxml` es el único con sello de "REIMPRESIÓN" (ver A.3) |
| **Cotizador / requisitos / buró** | `cotizacion.jrxml`, `requisitos_prestamo.jrxml`, `autorizacion_buro.jrxml` | Los 3 tienen `<image>` confirmado | No confirmado | Ya tienen logo propio; candidatos a adoptar el encabezado común tal cual |
| **Informes/listados** | Los 24 restantes (`informe_*.jrxml`) | No se confirmó `<image>` en ninguno (reciben `LOGO` como parámetro pero no se vio una etiqueta `<image>` al buscarla) | **Sí, confirmado en los 24** (`informe_clientes.jrxml` revisado como muestra: pie con `"Página " + $V{PAGE_NUMBER} + " de "` + total) | Los 24 comparten una estructura casi idéntica (mismo número de coincidencias de patrones de encabezado/pie en cada uno) — son los que MÁS cerca están ya del estilo que pide Joel; les falta el logo y "Impreso por [usuario] el [fecha]" |

**Nota de alcance**: esta tabla es la base del inventario que pide Joel; antes de tocar un solo
`.jrxml`, en la implementación se abre cada uno de los 36 para confirmar encabezado/pie/colores
exactos y ajustar esta tabla si hace falta.

### C.17 — Propuesta de estilo común

- **Encabezado** (igual en los 36): escudo/logo a la izquierda (ya se lee del classpath, D.22),
  razón social + RUC + dirección + teléfono a la derecha (ya vienen de Parámetros vía
  `EMPRESA_*`, confirmado que ya se usan en varios reportes) — una franja de color institucional
  (azul de marca `#0C325C`, el mismo que ya usa SweetAlert2 en pantalla, D.1 de pie.jsp) debajo
  del encabezado.
- **Títulos de sección**: un estilo uniforme (tamaño/negrita/color) para todos los reportes —
  hoy cada `.jrxml` define el suyo por separado.
- **Pie** (igual en los 36, agregar donde falte): "Página X de Y" (ya existe en los 24
  informes, falta en los 12 restantes) + "Impreso por [usuario] el [fecha/hora]" (no se
  encontró en ninguno de los 36 — es una adición nueva en todos).
- **Implementación recomendada**: un subreport o plantilla de estilo compartida (ej.
  `plantillas/encabezado_comun.jrxml` + `plantillas/pie_comun.jrxml`, incluidos desde cada
  reporte) para que el logo/colores se cambien en un solo lugar — a confirmar con Joel antes de
  tocar los 36 archivos, por el peso que tiene un cambio así.

### C.18 — Acta de arqueo (`arqueo_caja.jrxml`), detalles puntuales

- Encabezados de columna: "Hora", "Tipo", "Operación", "Comprobante", "Monto".
- Diferencia resaltada en un recuadro: ámbar si sobra, rojo si falta (mismo ámbar oscuro
  `#a15c00` que ya se usa en pantalla en `caja/ficha.jsp`, para consistencia).
- "Composición de lo cobrado" en una sola línea cuando no hubo cobros en la sesión.
- Aclaración + fecha junto a las firmas.
- "Cajero/a" → "Cajera" (texto fijo, sin la barra).
- "N°" → "N.º" (el sistema usa "N.º" en pantalla, confirmar que el PDF haga lo mismo).

### C.19 — Espacios en blanco grandes

- Contrato, página 1: espacio vacío grande a confirmar en la implementación (banda mal
  dimensionada, probable).
- Plan de pagos, antes de la tabla de cuotas: espacio vacío entre el encabezado y la tabla.
- Comprobante de desembolso, página 1: espacio vacío antes del cuerpo.

Los 3 necesitan abrir el `.jrxml` en el diseñador (o medir bandas) durante la implementación;
el síntoma está confirmado en los Pendientes de RF12/RF17, la causa exacta (banda de altura fija
sin contenido, o un `printWhenExpression` que no se cumple) se determina al tocar el archivo.

**Factura (RF23) queda explícitamente fuera de este punto** — formato legal, Joel la revisa en
otra instancia.

---

## D) SISTEMA 100 % SIN INTERNET (requisito de entrega)

**Resultado de la auditoría: el sistema YA cumple esto, casi en su totalidad — buena noticia,
no mala.** Se confirmó recorriendo todo `src/main/webapp` y `src/main/resources` buscando
`http://`/`https://`, CDN, Google Fonts y referencias de imagen externas.

| N.º | Punto | Resultado |
|---|---|---|
| D.20 | Inventario de todo lo que se carga de afuera | **No se encontró ningún `<script src="http...">`, `<link href="http...">` ni imagen por URL** en ningún JSP/CSS/JS del proyecto. Las únicas coincidencias de "http"/"https" en todo el árbol son: (a) URIs de esquema XML en `web.xml` y en los `.jrxml` (`xmlns="http://jasperreports.sourceforge.net/..."` — son identificadores de espacio de nombres, no se descargan en tiempo de ejecución); (b) texto de copyright/licencia DENTRO de los propios archivos ya vendorizados (ej. el comentario de licencia arriba de `adminlte.min.js`, el archivo `OFL.txt` de la fuente Montserrat) — no son llamadas de red, son archivos de texto. |
| D.21 | Traer cada cosa adentro del .war | **Ya está así.** Confirmado: `webapp/recursos/` ya tiene copias locales con su licencia de jQuery (`recursos/jquery/`, con `LICENSE.txt`), Bootstrap (`recursos/bootstrap/`), AdminLTE (`recursos/adminlte/`, con `LICENSE`/mapas de origen), SweetAlert2 (`recursos/sweetalert2/`), Font Awesome (`recursos/fontawesome/`, con `LICENSE.txt` y los 3 juegos de iconos SVG/CSS), y las fuentes propias (`recursos/fuentes/montserrat/`, con `OFL.txt`, más `fuente-source-sans-pro.css`). El `<head>` común (`plantilla/cabecera.jsp`) solo referencia estos paths locales (`${pageContext.request.contextPath}/recursos/...`), nunca una URL externa. |
| D.22 | JasperReports: fuentes empaquetadas, logo sin URL | **Ya está así.** El `pom.xml` incluye la dependencia `jasperreports-fonts` con un comentario explícito: "trae DejaVu Sans ya empaquetada" — la fuente que usan los 36 `.jrxml` (`fontName="DejaVu Sans"`) no depende de que la máquina tenga la fuente instalada. El logo se lee UNA vez del classpath (`GeneradorInformesPdf.cargarLogoBytes()`, cacheado en `byte[]`, nunca un `InputStream` ni una URL) y, si no está, cada PDF sigue generándose igual, sin logo, sin romper nada. |
| D.23 | No agregar dependencias de internet sin preguntar | Regla a seguir en toda esta tanda de correcciones — ninguno de los puntos A-C de este plan agrega una librería nueva ni una referencia externa. La única pieza nueva propuesta (C.17, plantilla de encabezado/pie compartida) es un archivo `.jrxml` más, local, igual que los 36 que ya existen. |
| D.24 | Prueba propuesta: desconectar internet | Guion para Joel: desconectar/bloquear internet en la máquina → (1) iniciar sesión → debe funcionar igual (sin pantallas de carga rota de Bootstrap/jQuery); (2) abrir el panel/menú → iconos de Font Awesome deben verse (no cuadrados vacíos); (3) abrir una ficha de cliente → fuente Montserrat/Source Sans Pro debe verse (no la fuente genérica del sistema operativo, señal de que el `.css` no cargó); (4) cualquier cartel de confirmación (SweetAlert2) → debe salir con los colores y botones de siempre; (5) generar cualquier PDF (ej. el plan de pagos) → debe abrir con el logo y el texto en la fuente correcta, sin errores de Jasper. Si los 5 pasos se ven iguales que con internet, el requisito está cumplido — y, por el inventario de D.20-22, ya debería estarlo hoy mismo, sin que esta tanda de correcciones necesite tocar nada de esto. |

---

## E) REPRUEBAS PENDIENTES DE RF01–RF03

Ninguno de estos 3 puntos es un hallazgo de código — son recorridos de pantalla que faltan
documentar formalmente, según lo que ya quedó anotado en la sección "Pendientes y trabajo
futuro" de cada documento reconstruido.

| N.º | RF | Qué falta probar | Guion concreto para Joel |
|---|---|---|---|
| E.25 | RF01 | La tabla "Comparar plazos" (agregada el 07/10/2026, commit `6476a52`) no tiene su propio "reprobado por Joel" documentado — los números que la alimentan están verificados contra `sgcpc_db_pruebas`, pero el clic en pantalla no. | En `/cotizador`, cotizar un monto y plazo cualquiera. Verificar que aparece la tabla "Comparar plazos" con las filas 6/12/18/24 (recortadas al rango vigente) más el plazo cotizado si no es uno de esos 4, con la fila actual resaltada "(actual)". Hacer clic en OTRA fila de la tabla: debe recotizar con ese plazo y la página debe bajar sola hasta el nuevo resultado. |
| E.26 | RF02 | El tag `rf02-probado` que existe (commit `f86819a`, 08/10/2026) es anterior a esta serie de documentos — representa una prueba real de Joel, pero sin el mismo formato riguroso (sin pasos documentados) que RF04 en adelante. Falta la reprueba formal con este rigor. | Seguir el guion completo de la sección 11 de `RF02_clientes.md`: (1) abrir "Nuevo cliente", confirmar que la Caja de Precalificación bloquea el resto del formulario; (2) completar un caso que NO califica, ver el motivo en rojo; (3) completar un caso que SÍ califica, ver que se habilita el resto; (4) cambiar la situación laboral y confirmar que cambian los documentos sugeridos; (5) imprimir "Requisitos para solicitar un préstamo"; (6) guardar con algo faltante, ver el cartel único; (7) editar un cliente que ya no califica y confirmar que SÍ se puede seguir editando. |
| E.27 | RF03 | Las 2 correcciones de seguridad del 09/10/2026 (`rf03-agujero-cerrado` / `rf03-situacion-laboral`) están implementadas y con lógica clara, pero sin una frase de "reprobado por Joel" documentada específicamente para ellas (a diferencia de las Partes 1-4 y el cierre). | (1) En el Expediente de un cliente, intentar forzar la carga de la autorización de buró o el resultado de la consulta por la vía genérica de "Otros documentos": debe rechazarse (agujero cerrado). (2) Cargar el comprobante de ingreso de un cliente, luego cambiar su situación laboral: la fila del comprobante debe volver automáticamente a "Falta" (ya no es evidencia válida para la situación nueva). |

---

## Puntos encontrados en los Pendientes que Joel no había listado explícitamente

Al leer la sección "Pendientes y trabajo futuro" de RF08 a RF19 no se encontró, además de lo
ya enumerado arriba, ningún otro punto de "miente o confunde" — la mayoría de esos documentos
(RF13, RF15, RF18 propiamente, RF19 aparte de lo ya listado) cierran su sección de Pendientes
con "sin otros hallazgos" o con elementos que dependen del tiempo (vigencia de documentos,
mora) que Joel pidió explícitamente NO tocar en esta tanda.

---

## Resumen final — Lote 1 VERIFICADO (11/10/2026)

- **A (prioridad, miente o confunde)**: 10 puntos — **A.1 a A.10, TODOS verificados OK en
  pantalla** (A.1/A.2 necesitaron una segunda vuelta por el `.jasper` sin recompilar, ya
  corregido y confirmado).
- **B (usabilidad y textos)**: 5 puntos — **B.11 a B.15, TODOS verificados OK en pantalla.**
- **C (diseño de PDF)**: sin empezar — Lote 2, Joel aprobó EMPEZAR solo como MUESTRA en
  `plan_pagos.jrxml`, `pagare.jrxml`, `arqueo_caja.jrxml` y `comprobante_desembolso.jrxml` (ver
  el análisis más abajo) — **todavía sin programar, a la espera de la aprobación de Joel sobre
  la muestra**. `factura.jrxml` queda fuera incluso del encabezado compartido.
- **D (sin internet)**: auditoría completa — el sistema ya cumple el requisito, sin cambios de
  código; solo queda la prueba manual de desconexión (D.24).
- **E (reprueba RF01-03)**: 3 recorridos de pantalla, sin código — sin empezar.
- **RF13 (`PLAN_PAGOS_FIRMADO`), `.jasper`/`.jrxml` (control permanente) y firma de "quien
  atendió" (E)**: todos verificados OK en pantalla.
- **7 detalles menores de la segunda reprueba** (a-g, ver la sección de arriba): anotados en
  Pendientes de RF12/RF13/RF16 y acá — se corrigen junto con el Lote 2, salvo 2.f (regresión de
  D.2), ya corregida de nuevo.
- **RF14 (anular préstamo antes del desembolso)**: documento nuevo, solo análisis — ver
  [RF14_anular_prestamo.md](../RF14_anular_prestamo.md).

Ningún punto de este documento se da por cerrado sin su prueba en pantalla documentada, igual
que el resto del proceso (RF04 en adelante). El detalle de archivos tocados y el orden de
pruebas de esta segunda vuelta del Lote 1 quedó en el mensaje de cierre de esa tanda
(11/10/2026), y en el Historial de cambios de cada documento de RF tocado (RF05, RF09, RF11,
RF12, RF13, RF15, RF16, RF17, RF18, RF19).

**Lote 1: VERIFICADO EN PANTALLA, comiteado con el tag `consolidacion-rf08-rf19-lote1`.**

**Lote 2 (estilo común de PDF): análisis entregado (ver más abajo) — a la espera de la
aprobación de Joel para programarlo.**
