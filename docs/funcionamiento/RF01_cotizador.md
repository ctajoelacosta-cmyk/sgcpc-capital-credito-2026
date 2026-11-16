# RF01 — Cotizador (simular un préstamo sin registrar nada)

**Estado: REPROBADO FORMALMENTE el 14/10/2026 por Joel (perfil oficial, `sgcpc_db_pruebas`) —
tag `rf01-reprobado`. Antes: probado en pantalla desde el 07/10/2026 — tag `rf01-probado`
(correcciones) y `rf01-comparar-plazos` (mejora posterior). Documento reconstruido el
11/10/2026 a partir del historial de git.**

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF01 (módulo Clientes y solicitudes):

> El sistema debe permitir simular un préstamo indicando monto, plazo y fecha del primer
> vencimiento, y mostrar e imprimir el plan de cuotas sin registrar ningún dato.

## 2. Cómo funciona

`/cotizador` (abierto a los 3 perfiles, ver sección 5): un formulario con monto, plazo y fecha
del primer vencimiento (más nombre y C.I. del interesado, opcionales, solo para el PDF) —
`POST /cotizador` llama a `ServicioCotizacion.cotizar` (que delega en
`ServicioMotorFinanciero.cotizar`, el mismo motor que usa toda simulación/aprobación/formalización
del sistema) y muestra el resultado en la misma página, sin guardar nada en la base.

Lo que se ve, en orden:
1. **Resultado principal**: cuota (con IVA, resaltada), monto total, TEA/TNA/CFT.
2. **Tabla "Comparar plazos"** (agregada el 07/10/2026): Plazo | Cuota (1.ª, con IVA) | Total a
   pagar, para 6/12/18/24 meses recortados al rango vigente
   (`credito.plazo_minimo`/`plazo_maximo`) más el plazo cotizado si no es ninguno de esos 4,
   insertado en su lugar ordenado. La fila del plazo actual queda resaltada ("(actual)"). Un
   clic en cualquier fila vuelve a cotizar con ese plazo (mismo `POST`, sin fórmula nueva) y la
   página baja sola hasta el resultado.
3. **Plan de cuotas completo** (tabla en pantalla).
4. **Botón "Imprimir"**: genera un PDF con el mismo resultado, más una caja de glosario (TEA,
   TNA, CFT, IVA, Capital, Interés — los 6 términos con el texto EXACTO que aprobó Joel, sin la
   palabra "capitaliza/capitalizar") y los datos de la empresa (logo, teléfono/WhatsApp).
5. **Botón "Limpiar"**: vacía el formulario y el resultado, sin confirmación.

**Nunca se guarda nada**: no hay ningún `INSERT` en el camino de `/cotizador` — cumple
literalmente "sin registrar ningún dato" del texto. El PDF tampoco se guarda server-side
(se genera al vuelo en cada clic de "Imprimir", a diferencia de los documentos de formalización
que sí se guardan una vez generados).

## 3. Auditoría

**No aplica** — cotizar no registra nada en la base, así que no hay ninguna fila que auditar
(ni éxito ni error). Es la única pantalla operativa del sistema sin ningún evento de auditoría
propio, consistente con que el texto pide explícitamente "sin registrar ningún dato".

## 4. Dónde está

- `servicios/ServicioCotizacion.java` — `cotizar`, `construirPlazosComparacion` (pura, con
  pruebas), `validarRangoFechaPrimerVencimiento`, `validarPlazoDentroDeLimites`.
- `servicios/ServicioMotorFinanciero.java` — el cálculo real (`cotizar`/`generarPlanPagos`),
  compartido con RF05/RF11 (motor financiero único, `REGLAS_DESARROLLO.md` §5).
- `infraestructura/GlosarioFinanciero.java` — los 6 términos (texto corto + nombre expandido +
  `getTitulo()`), usados en el modal ⓘ de la pantalla y en la caja del PDF.
- `controladores/ControladorCotizadorServlet.java` (`GET`/`POST /cotizador`,
  `POST /cotizador/informe` para el PDF).
- `webapp/WEB-INF/vistas/cotizador/formulario.jsp`.
- `src/main/resources/reportes/cotizacion.jrxml`.
- `dto/FilaComparacionPlazo.java`.

## 5. Dependencias

- **Depende de**: `ServicioMotorFinanciero` (motor único); parámetros `credito.plazo_minimo`/
  `plazo_maximo`/`tea_empresa`/montos límite.
- **Lo afecta un cambio en**: cualquier cambio del motor financiero se refleja automáticamente
  (sin tocar esta pantalla).
- **A quién afecta un cambio acá**: a nadie — el cotizador es una hoja de cálculo pública sin
  efecto en el resto del sistema (no crea clientes, solicitudes ni nada que otro RF dependa de).

## 6. Análisis

### Cumplimiento contra el texto firmado

El texto se cumple **sin excepción**: se puede simular indicando monto, plazo y fecha del
primer vencimiento; se muestra e imprime el plan de cuotas; no se registra ningún dato. No se
encontró ninguna parte del texto sin cumplir.

### Cosas ya corregidas en el camino (de los commits reconstruidos, sección 8)

- El glosario financiero usaba textos distintos de los que Joel aprobó (incluía "capitalizan",
  justo la palabra técnica que se quería evitar) — corregido a los 6 textos exactos.
- Un bug real de formato: el texto de TEA/TNA tenía un "%%" de más (`FormateadorNumeros.comoPorcentaje`
  ya agrega el " %") — mostraba "27,39 % %".
- El PDF (con el logo y el glosario de 6 términos) tuvo 2 rondas de ajuste de maquetación
  (bandas superpuestas, columna única que no entraba) antes de quedar aprobado.

## 7. Decisiones

- El glosario usa el texto EXACTO que dio Joel para los 6 términos, sin aproximaciones ni
  sinónimos técnicos.
- "Comparar plazos" recorta siempre al rango vigente de plazo (nunca ofrece un plazo fuera de
  los límites del parámetro), e inserta el plazo cotizado en su lugar ordenado si no es uno de
  los 4 de referencia (6/12/18/24).
- El PDF se regenera siempre al vuelo (nunca se guarda una copia) — distinto del patrón de
  "generar al primer pedido y guardar" que sí usan los documentos de formalización/comprobantes,
  porque el cotizador no tiene una fila persistente a la que asociar un archivo.

## 8. Historial de cambios

*(Reconstruido desde `git log` el 11/10/2026 — este RF no tuvo un documento propio hasta ahora;
las fechas y los "reprobado por Joel" vienen de los mensajes de commit reales, no de una
reconstrucción aproximada.)*

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 24-25/09/2026 | Implementación base del cotizador junto con clientes/documentos/buró (E3) | `153e612`/`741963f` | — |
| 07/10/2026 | 9 grupos de mejoras de pantalla (límites de monto/plazo, columna Saldo, botones Limpiar/Nueva cotización, rango de fecha, glosario, PDF con logo/glosario, nombre y C.I. separados) | `3903b46`…`b1b4da2` | — |
| 07/10/2026 | Correcciones tras la prueba de Joel en el navegador (glosario con el texto exacto, bug del "%%", botón "Entendido", maquetación del PDF) — **reprobado por Joel, OK** | `8a41765` | `rf01-probado` |
| 07/10/2026 | Salto automático al resultado + tabla "Comparar plazos" — verificado contra `sgcpc_db_pruebas` con los números exactos que dio Joel (5.000.000 Gs., 27/10/2026: 12 meses = 483.199, 24 meses = 274.767) | `6476a52` | `rf01-comparar-plazos` |

## 9. Pruebas realizadas en pantalla

**07/10/2026 (Joel), en el navegador** (commit `8a41765`): confirmó los 3 puntos de la
corrección (glosario con el texto exacto, el bug del "%%" resuelto, el botón "Entendido" en vez
de "OK"). Este es el cierre formal de RF01 — tag `rf01-probado`.

**07/10/2026**, la tabla "Comparar plazos" (commit `6476a52`) se verificó contra
`sgcpc_db_pruebas` con los números exactos que Joel dio como caso de prueba — el mensaje del
commit no dice explícitamente "reprobado en el navegador" para esta mejora puntual, a
diferencia del resto de los commits de este RF, que sí lo dicen. **No hay una confirmación de
pantalla documentada específicamente para "Comparar plazos"**, aunque los números que la
alimentan están verificados.

**14/10/2026 (Joel), REPRUEBA FORMAL, perfil oficial, `sgcpc_db_pruebas`** — tag
`rf01-reprobado`: cotizador con 3.000.000 Gs./12 meses/1.er vencimiento 03/11/2026 → cuota sin
IVA 283.884, 1.ª cuota con IVA 289.919, total 3.447.270, TNA 24,1413 %, CFT 30,0384 %;
"Comparar plazos" con las 4 filas 6/12/18/24 — confirma en pantalla lo que faltaba de la
sección anterior; PDF con logo y la caja de glosario; monto 400.000 y plazo 30 meses
rechazados (fuera de rango); confirmado "No se guarda ningún registro" (coherente con la
sección 3, sin auditoría). **Resultado: APROBADO, sin hallazgos nuevos.**

**14/10/2026, Tanda 2, probado en pantalla por Joel** — tag `mejoras-tanda2-probado`: el mismo
monto 400.000 Gs. (fuera de rango) ahora sale como advertencia ("!") con botón "Entendido", no
como error (X roja). **OK.** Mismo mecanismo (`mensajeAdvertencia` en vez de `mensajeError`)
aplicado también a `ControladorUsuariosServlet`/`ControladorClientesServlet` (comparten el
mismo `pie.jsp`/`dividirEnTituloYDetalle`) — no se probó un caso de error específico en esas dos
pantallas en esta tanda, solo en Cotizador.

## 10. Pendientes y trabajo futuro

Ninguno — RF01 cumple el texto sin excepción (sección 6) y la reprueba formal del 14/10/2026
confirmó en pantalla, incluida la tabla "Comparar plazos" (único punto que quedaba abierto).

## 11. Guion para la defensa

1. Cotizar un monto y plazo cualquiera — mostrar el resultado, la cuota con IVA resaltada y el
   total.
2. Mostrar la tabla "Comparar plazos" — clic en otro plazo, mostrar que recotiza y salta al
   resultado.
3. Abrir el glosario (ⓘ) — mostrar los 6 términos con el texto exacto, sin la palabra técnica
   "capitaliza".
4. Imprimir el PDF — mostrar el plan completo, la caja de glosario y los datos de la empresa.
5. Confirmar que nada de esto se guardó: no hay ningún registro nuevo en ninguna bandeja del
   sistema después de cotizar.
