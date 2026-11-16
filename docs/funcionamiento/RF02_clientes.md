# RF02 — Registrar clientes (datos personales, domicilio, situación laboral, ingresos, deudas, referencias)

**Estado: REPROBADO FORMALMENTE el 14/10/2026 por Joel (perfil oficial, `sgcpc_db_pruebas`) —
tag `rf02-reprobado`. Antes: implementado y probado de forma informal en el navegador
(08/10/2026, tag `rf02-probado`), antes de que existiera este documento.**

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF02 (módulo Clientes y solicitudes):

> El sistema debe permitir registrar clientes con sus datos personales, domicilio, situación
> laboral, ingresos, deudas y referencias.

## 2. Cómo funciona

`/clientes/nuevo` (alta) y `/clientes/{id}` (ficha/edición), protegidos por
`Permiso.REGISTRAR_EDITAR_CLIENTE` (gerente y oficial TOTAL, cajera solo CONSULTA — ve la
ficha, no puede crear ni editar).

**Caja de Precalificación** (primera sección del formulario, agregada el 08/10/2026): los 4
datos que definen si una persona califica — fecha de nacimiento (con la edad calculada en
vivo), departamento (solo los habilitados por la política vigente), situación laboral (solo
las admitidas) y "¿puede presentar un comprobante de su ingreso?" (Sí/No). Un indicador en vivo
(`GET /clientes/precalificar`, `ServicioClientes.evaluarPrecalificacion` — de solo lectura,
nunca bloquea nada por sí sola) muestra gris mientras falte algún dato, verde "Califica" o rojo
"No califica: [motivo]" una vez completos los 4. **En el ALTA**, el resto del formulario arranca
deshabilitado y solo se habilita si califica. **En la EDICIÓN** de un cliente existente, el
resto del formulario nunca se deshabilita — se puede seguir editando aunque ya no califique
(con el aviso amarillo de siempre), porque la persona ya existe y editarla no es "aceptar un
nuevo cliente que no califica".

**Resto del formulario**, reordenado en 5 secciones: Datos personales (cédula, nombres, estado
civil, cónyuge) · Domicilio · Contacto · Trabajo e ingresos (situación laboral, empleador,
ingresos, otras deudas) · Referencias (2 personales, la primera obligatoria desde el 08/10/2026
tanto en alta como en edición).

**Con una evaluación en curso, los datos de la evaluación quedan congelados** (2c, pedido de Joel
del 17/10/2026): mientras el cliente tenga una solicitud `PENDIENTE_APROBACION`/`APROBADA` sin
formalizar o un préstamo pendiente de desembolso, los **13 datos que definieron esa evaluación** no
se pueden cambiar — fecha de nacimiento y ciudad del domicilio (definen edad y zona evaluadas),
situación laboral, "presenta comprobante", ingreso neto, cuota de otras deudas, y los 6 del
empleador (nombre, RUC, dirección, teléfono, cargo, antigüedad). **El resto de la ficha se sigue
editando igual que siempre** (nombres, cédula y su vencimiento, domicilio salvo la ciudad,
contacto, cónyuge, referencias, y "otros ingresos"/"gastos fijos", que son informativos): D-129,
"la edición siempre guarda". La pantalla los muestra **en gris** y el servidor revalida lo mismo
(`ServicioClientes.cambiaronDatosDeEvaluacion`, puro) — los dos criterios son la misma lista, no
dos copias.

**El aviso dice cuáles son** (B91, decisión de Joel del 29/10/2026): el mismo texto tiene **dos
arranques** según dónde se lee, con un parámetro de contexto
(`ServicioClientes.ContextoAvisoEvaluacion`) sobre un solo método —
*"**Los datos en gris** no se pueden modificar porque…"* en los banners de la ficha (que conviven
con campos editables, de ahí que haga falta señalar cuáles), y *"**Los datos de la evaluación** no
se pueden modificar porque…"* en el rechazo del servidor, donde no hay nada gris que señalar.
Antes los dos decían *"Estos datos…"*, sin decir cuáles. El resto de la frase (qué operación lo
bloquea y qué puede hacer el usuario) no cambió, y **no se listan los campos**.

**Botón "Imprimir requisitos"**: genera un PDF "Requisitos para solicitar un préstamo" con los
documentos concretos que hacen falta según la situación laboral elegida (ver sección 6) — se
habilita apenas se elige la situación laboral, sin esperar al resto de la precalificación.

**Validaciones de servidor** (sin cambios desde la base, `ServicioClientes.alta`/`actualizar`):
política de crédito (edad, zona/departamento, situación laboral) con lista de motivos
("No califica: ..."); comprobante de ingresos obligatorio en el ALTA (nunca bloquea la EDICIÓN,
una persona ya cargada no se "da de baja" por eso); cédula duplicada (verificada al salir del
campo, con el nombre del cliente al que ya pertenece); un solo cartel con TODO lo que falta,
nunca una lista parcial.

### Baja y reactivación

Baja lógica — **nunca `DELETE`** (REGLAS_DESARROLLO.md §5): `POST /clientes/{id}/baja` (desde el
listado, `clientes/_resultados.jsp`, con motivo obligatorio de una lista cerrada —
`FALLECIMIENTO`/`PEDIDO_CLIENTE`/`DATOS_DUPLICADOS`/`OTRO` — y un detalle opcional) pone
`estado_persona = INACTIVA` y guarda el motivo/detalle/fecha/usuario en `personas_baja`
(normalización de `personas`, grupo Baja, 16/10/2026 — antes vivían como columnas de `personas`).
Solo el gerente o el administrador (`ServicioClientes.darDeBaja`). Se **rechaza** si el cliente
tiene algún préstamo `ACTIVO`/`EN_MORA`, o una solicitud abierta (borrador, pendiente o aprobada
sin formalizar — mismo mecanismo que RF05/RF06, "Cancélela primero" con un enlace real a la
solicitud). Un cliente `INACTIVA` no puede iniciar solicitudes nuevas; la ficha muestra el aviso
con motivo/detalle/fecha de la baja.

`POST /clientes/{id}/reactivar` (mismo permiso, solo gerente/administrador,
`ServicioClientes.reactivar`) vuelve `estado_persona = ACTIVA` — **y conserva en `personas_baja`
el motivo/detalle/fecha/usuario de la ÚLTIMA baja como historial**, sin tocarlos ni borrarlos (si
se la vuelve a dar de baja más adelante, esa fila se sobreescribe con los datos de la baja
nueva). Este comportamiento viene de antes de la normalización y está probado por
`pruebas/humo_http.sh`.

### Domicilio (normalización de `personas`, grupo Domicilio, 16/10/2026)

Los 7 campos de domicilio (Ciudad, Barrio, Dirección, Referencia opcional, Tipo de vivienda,
Antigüedad) viven ahora en `personas_domicilio` (1–1, siempre existe — ninguna persona queda sin
fila), en vez de columnas de `personas`. `ServicioClientes.validarDomicilio` sigue exigiendo los
mismos 5 obligatorios de siempre (ciudad, dirección, barrio, tipo de vivienda, antigüedad); la
pantalla no cambió — mismos campos, mismo orden, misma sección "Domicilio" de la ficha.

**Dirección y barrio se guardan capitalizados** (B13, 01/11/2026): `validarDomicilio` les pasa el
**mismo** `NormalizadorTexto.normalizarNombrePropio` que ya normalizaba los nombres, así que
"general santos 123" se guarda "General Santos 123" y "BARCEQUILLO" queda "Barcequillo". El
motivo es que estos dos campos **se imprimen en documentos legales** (contrato, comprobante de
desembolso y factura, ver `RF12_documentos_pdf.md`), donde el texto tal como se tipea queda mal.
La **referencia** (coloquial, "casa blanca con portón verde") sigue con `normalizarTextoLibre`:
no va en ningún documento legal (B.15). **Los datos ya cargados no se migran** (decisión de Joel):
un domicilio viejo sigue imprimiéndose como se guardó hasta que alguien edite esa ficha.

**El Departamento ya no se guarda**: el `<select>` de Departamento sigue en la caja de
Precalificación (filtra el `<select>` de Ciudad en el navegador y entra en la precalificación),
pero al guardar solo se usa para validar que la ciudad elegida le pertenezca (D-126) — el valor en
sí nunca se persiste. En toda la aplicación (política de crédito, resumen de solicitudes y
aprobaciones, informes, filtro de la lista de Clientes) el departamento de una persona se obtiene
siempre por `JOIN` desde su ciudad (`ciudades.id_departamento` — dependencia transitiva, 3FN). En
la ficha, tras guardar y recargar, el `<select>` de Departamento muestra el que corresponde a la
ciudad guardada (nunca uno "recordado" aparte).

**El texto legal del domicilio ya no se guarda**: antes se armaba una sola vez al guardar la
ficha y quedaba fijo en `personas.domicilio`; ahora `ServicioClientes.obtenerTextoDomicilio` lo
arma al vuelo (JOIN a `ciudades`/`departamentos` + `construirTextoDomicilio`, la misma lógica/
orden/puntuación de siempre) cada vez que un PDF lo necesita — nunca se guarda un texto fijo. Los
PDF ya emitidos no cambian (D-94): la lectura solo ocurre al generar un PDF nuevo. Las 2 columnas
legado `direccion_laboral`/`referencias` (sin dato real, reemplazadas hace tiempo por el campo
estructurado y por las referencias estructuradas) se eliminaron de `personas` sin reemplazo.

### Trabajo e ingresos (normalización de `personas`, grupo Laborales, 17/10/2026 — ÚLTIMO GRUPO)

Los campos de situación laboral, empleador, ingresos y cuota de otras deudas viven ahora en
`personas_laborales` (1–1, siempre existe — ninguna persona queda sin fila), en vez de columnas
de `personas`. `ServicioClientes.validarLaboral` sigue exigiendo lo mismo de siempre: situación
laboral obligatoria; los 5 datos del empleador (`empleador_nombre`/`ruc`/`direccion`/`telefono`/
`cargo`) obligatorios solo si la situación es `ASALARIADO_SECTOR_PRIVADO` o
`FUNCIONARIO_PUBLICO` (comerciante, independiente, jubilado, doméstico, desempleado u otro no los
piden); `ingreso_neto_comprobado`/`cuota_otras_deudas_mensual` siempre obligatorios (0 si no
tiene otras deudas); `otros_ingresos_mensuales`/`gastos_fijos_mensuales` informativos, nunca
entran en la fórmula de capacidad de pago. La pantalla no cambió — mismos campos, mismo orden,
misma sección "Trabajo e ingresos" de la ficha, mostrando/ocultando los campos del empleador
según la situación elegida (confirmado en pantalla: comerciante sin esos campos; cambio a
Asalariado los exige; vuelta a Comerciante, desaparecen sin exigirlos).

**Único punto de lectura para capacidad de pago**: `ServicioClientes.obtenerLaboral` reemplaza
las lecturas directas de `Persona` en los 4 lugares que necesitan ingreso/cuota de otras deudas
(`ServicioSolicitudes.crear`/`simular`/`enviarAAprobacion`, revalidación RF11 de
`ServicioFormalizacion`) — mismo criterio que `obtenerTextoDomicilio` del grupo Domicilio.
`solicitudes_credito.ingreso_cliente_evaluado`/`porcentaje_afectacion` (el snapshot congelado al
enviar a aprobación) no cambiaron de comportamiento: siguen leyendo el valor actual de la persona
en los mismos 3 momentos de siempre, solo que ahora ese valor viene de `personas_laborales`.

**Cambiar la situación laboral invalida el comprobante de ingreso vigente** (regla RF03
preexistente, confirmada idéntica desde antes de esta normalización, ver
`RF03_expediente.md §8`, `rf03-situacion-laboral`): el documento queda `REEMPLAZADO` sin subir
ningún archivo nuevo. Pendiente de usabilidad registrado (sin implementar):
`RNF_revision.md §10f`, avisar ANTES de guardar.

**`lugar_trabajo` se elimina sin reemplazo**: columna legado que solo alimentaba un *fallback* de
pantalla (precompletar "Dirección laboral" si `empleador_direccion` estaba vacío) ya innecesario
— se quita la columna, el *hidden* de `ficha.jsp` y el *fallback* en sí.

## 3. Auditoría

Evento de alta/edición de cliente (entidad PERSONA) — quedó así desde la base del sistema (E3),
sin cambios documentados específicamente para RF02 en el historial reconstruido. Las políticas
de crédito que bloquean el alta (edad/zona/situación laboral/comprobante) no generan su propio
evento: son validaciones previas al `INSERT`, no hay nada que auditar si no llegan a guardarse.

## 4. Dónde está

- `servicios/ServicioClientes.java` — `alta`, `actualizar`, `evaluarPoliticaCredito`,
  `evaluarPrecalificacion`, `listarMotivosNoCalifica`, `descripcionComprobanteIngresoSegunSituacionLaboral`,
  `listarSituacionesLaboralesParaFiltro`, `validarDomicilio`, `validarCiudadPerteneceADepartamento`,
  `obtenerDomicilio`, `mapaIdCiudadPorPersona`, `nombreDepartamentoDesdeCiudad`,
  `obtenerTextoDomicilio` (JOIN + `construirTextoDomicilio` — domicilio compuesto, dirección +
  barrio + ciudad + departamento + referencia, armado al vuelo para cada PDF, nunca guardado — ver
  también `RF12_documentos_pdf.md` §10d para un efecto colateral de este mismo método en los PDF
  legales); `validarLaboral`, `obtenerLaboral` (único punto de lectura de ingreso/cuota de otras
  deudas, grupo Laborales, 17/10/2026).
- `dao/PersonaDomicilioDAO.java` (nuevo, grupo Domicilio, 16/10/2026) / `modelos/PersonaDomicilio.java`.
- `dao/PersonaLaboralDAO.java` (nuevo, grupo Laborales, 17/10/2026) / `modelos/PersonaLaboral.java`.
- `dao/PersonaDAO.java` (`listarSituacionesLaboralesDistintas`, para el filtro del listado).
- `controladores/ControladorClientesServlet.java` (`GET`/`POST /clientes`,
  `GET /clientes/precalificar`, `POST /clientes/requisitos`).
- `webapp/WEB-INF/vistas/clientes/ficha.jsp`, `clientes/lista.jsp`.
- `src/main/resources/reportes/requisitos_prestamo.jrxml`.
- Parámetros de política: edad mínima/máxima, departamentos habilitados, situaciones laborales
  admitidas. (`credito.exigir_garante` ya no lo lee ningún servicio desde el retiro de codeudor,
  18/10/2026 — pendiente de eliminar en el Commit 3 del refactor.)

## 5. Dependencias

- **Depende de**: nada de otro RF — es el punto de entrada de todo el flujo de
  clientes/solicitudes.
- **Lo afecta un cambio en**: los parámetros de política de crédito (comparten
  `evaluarPoliticaCredito` con RF05/RF06, que vuelven a revalidar la misma política en otros
  momentos del flujo).
- **A quién afecta un cambio acá**: RF03 (expediente — depende de la situación laboral elegida
  acá), RF04 (buró, que además lee el domicilio en vivo en cada reimpresión — ver la sección de
  Domicilio más arriba), RF05 (solicitud — ingresos/otras deudas alimentan la capacidad de pago),
  RF12 (los PDF legales arman el domicilio al vuelo con `ServicioClientes.obtenerTextoDomicilio`,
  nunca lo leen ya armado).

## 6. Análisis

### Cumplimiento contra el texto firmado

El texto se cumple **sin excepción**: se registran datos personales, domicilio, situación
laboral, ingresos, deudas propias y referencias (2 personales). No se encontró, en el historial
reconstruido, ninguna parte del texto que haya quedado sin implementar.

### Hallazgo ya documentado en otro RF (referencia, no se repite el análisis)

El domicilio compuesto por `construirTextoDomicilio` (dirección + barrio + ciudad + departamento
+ "(referencia)", armado al vuelo por `ServicioClientes.obtenerTextoDomicilio` desde la
normalización del grupo Domicilio, 16/10/2026 — antes se guardaba una sola vez en
`personas.domicilio`) se usa tal cual en los PDF legales de formalización (contrato, pagaré,
comprobante de desembolso) — ahí aparece la referencia coloquial ("casa blanca con portón
verde") en un documento legal, hallazgo ya anotado en `RF12_documentos_pdf.md` §10d y
`RF17_comprobante_desembolso.md` §10d. No es un problema de RF02 en sí (el campo compuesto es
correcto y útil para uso operativo), solo de dónde se reutiliza después.

## 7. Decisiones

- **Precalificación primero, antes de llenar el resto del formulario** (decisión de Joel,
  08/10/2026): evita que el oficial complete toda la ficha para recién al final descubrir que el
  cliente no califica.
- **El bloqueo de "no califica" es solo para el ALTA, nunca para la EDICIÓN** de un cliente ya
  existente — editar no es lo mismo que aceptar un cliente nuevo.
- **El comprobante de ingresos bloquea el alta, nunca la edición** (mismo criterio que el punto
  anterior).
- **La primera referencia personal es obligatoria** (alta y edición) desde el 08/10/2026 — deja
  de ser un aviso amarillo de política y pasa a ser un campo obligatorio como cualquier otro.
- **Documentos sugeridos concretos por situación laboral** (comerciante: declaraciones de IVA o
  facturas + patente; doméstico/a: extracto de aportes IPS; jubilado: recibo de haberes) en vez
  de un texto genérico "a criterio del oficial".

## 8. Historial de cambios

*(Reconstruido desde `git log` el 11/10/2026 — este RF no tuvo un documento propio hasta ahora;
agrupado por hito, no commit por commit — son más de 20.)*

| Fecha | Qué cambió | Commit (representativo) | Tag |
|---|---|---|---|
| 01/11/2026 | **B13**: dirección y barrio se guardan capitalizados con el mismo normalizador de los nombres; la referencia no. Sin migrar lo ya cargado. **Probado en pantalla el 01/11/2026**: el domicilio de Ana re-guardado como "general santos 123" quedó "General Santos 123" y así salió en el contrato re-emitido de `CC-2026-00006` | `0d0b86d` | — |
| 24-25/09/2026 | Implementación base de alta/edición de clientes junto con documentos/buró/cotizador (E3) | `153e612`/`741963f` | — |
| 30/09/2026 | D-127 (Tanda B): política de crédito ampliada — situación laboral, empleador, otros ingresos/gastos, cuota de otras deudas, capacidad de pago con otras deudas | `ed04042` | — |
| 07/10/2026 | 10 grupos de mejoras (política de elegibilidad reorganizada, comprobante de ingresos solo bloquea alta, `<select>` de situación laboral/departamento filtrados, primera referencia obligatoria, verificación de C.I. al salir del campo, un solo SweetAlert de faltantes, mayúscula inicial al guardar) | `d5f4b09`…`33f04ed` | — |
| 08/10/2026 | 6 mejoras de pantalla tras la prueba de Joel (filtro de situación laboral, edad en vivo con color, siguiente paso en el mensaje de edición, textos, C.I. del cónyuge con puntos, aviso de duplicado) — **reprobado por Joel** | `7495062` | — |
| 08/10/2026 | Documentos sugeridos concretos por situación laboral (comerciante/doméstica/jubilado) | `07e5c1d` | — |
| 08/10/2026 | Caja de Precalificación al inicio de la ficha, formulario reordenado en 5 secciones, botón "Imprimir requisitos" | `801c753` | — |
| 08/10/2026 | PDF "Requisitos para solicitar un préstamo" | `c5c494c` | — |
| 08/10/2026 | Corrige la precalificación en edición (bug real: `CONTEXTO_APP` indefinido al cargar) | `39656f5` | — |
| 08/10/2026 | Ajustes finales de Precalificación (a-g: color del encabezado, PDF sin la línea de usuario/fecha arriba, botón Limpiar con confirmación, aviso del garante en el indicador en vivo, etiquetas de referencia, botones alineados, primera referencia obligatoria) + corrección de un mensaje duplicado que podía divergir — **reprobado por Joel en el navegador** | `f86819a` | `rf02-probado` |
| 16/10/2026 | Normalización de `personas`, grupo Domicilio: tabla `personas_domicilio` (1–1, siempre existe); el departamento ya no se guarda (siempre por JOIN desde la ciudad); el texto legal del domicilio ya no se guarda (se arma al vuelo para cada PDF); columnas legado `direccion_laboral`/`referencias` eliminadas sin reemplazo — probado en pantalla por Joel (oficial y gerente) | ver tag | `norm-domicilio-probado` |
| 17/10/2026 | Normalización de `personas`, grupo Laborales (ÚLTIMO GRUPO — normalización completa): tabla `personas_laborales` (1–1, siempre existe); columna legado `lugar_trabajo` eliminada sin reemplazo; `personas` queda en 15 columnas — probado en pantalla por Joel | ver tag | `norm-laborales-probado` |

| 29/10/2026 | **B91**: el aviso de los datos congelados dice **cuáles** — "Los datos en gris…" en la ficha, "Los datos de la evaluación…" en el rechazo del servidor, con un parámetro de contexto sobre un solo método (ningún literal nuevo, la regla y los 13 campos sin tocar). **Probado en pantalla el mismo día** (§9). `mvn test` 841/841 | *(este commit)* | — |
| 05/11/2026 | **B116**: los **cuatro** carteles de confirmación de esta ficha dejan de decir *"Cancelar"*/*"No"* en el botón de descartar y dicen **"No, volver"**, igual que los 24 carteles del mecanismo de `plantilla/pie.jsp` (que lo traen por defecto desde B3/B31, commit `f585f28`). Son los `Swal.fire` escritos a mano —la **otra familia** de carteles, que no pasa por `pie.jsp`— y por eso B3 no los había alcanzado: *¿Borrar los datos cargados?*, *ya existe un documento vigente de este tipo*, *ya existe una respuesta de Informconf vigente* y *la clasificación suele indicar atrasos*. **Dos textos de esta misma ficha se dejaron a propósito**: el aviso de **cédula ya registrada** sigue diciendo *"Cerrar"* porque **no es una confirmación** sino informativo (la acción es *"Ir a su ficha"*, así que *"No, volver"* prometería una vuelta que no existe). Solo texto: ningún `Swal.fire` cambió de comportamiento, ninguna validación, ningún dato. Lo cubre `TextosDePantallaTest`, verificado contra una violación | `c422c50` | — |
| 14/11/2026 | **B147**: el *"Volver"* de la ficha devuelve a la búsqueda de la lista (filtros **y** página), no a `/clientes` pelado. El dato lo guarda `ControladorClientesServlet.mostrarLista` en la sesión (`RETORNO_LISTA_EN_SESION`) y lo sirve `mostrarFicha`; la JSP solo dibuja el href. En la sesión y no en la URL para que sobreviva al redirect de después de guardar. **B148**: la paginación del listado pasa a « Anterior / primera / hasta cinco alrededor de la actual con … / última / Siguiente » más *"Página X de Y"*, conservando el contrato de `listado-vivo.js` (`listado-vivo-pagina` + `data-pagina` + href real); lo no navegable es `<span>` sin esa clase. Clientes es el único listado paginado del sistema, así que ese bloque queda como modelo. Ver `recorrido/FASE2_PLAN.md` § Clientes — tanda 1 de la revisión UX | *(este commit)* | — |
| 14/11/2026 | **B152**: la barra de *Registrar cliente / Volver* queda a la vista mientras se recorre el formulario (`sgcpc-tema.css` §10, `position: sticky`), y al lado muestra **"Faltan N datos obligatorios."** en vivo, en gris. El contador y el cartel al enviar salen de **la misma** función (`nombresDeLoQueFalta`), así que no pueden contar distinto; el contador se oculta mientras el `<fieldset>` está deshabilitado. Solo presentación: el servidor valida igual. Ver `recorrido/FASE2_PLAN.md` § Clientes — tanda 2 | *(este commit)* | — |
| 14/11/2026 | **B153**: debajo del `<select>` de Ciudad, una línea de ayuda dice qué **departamento** está elegido y dónde se elige (*"Departamento: Central — se elige arriba, en Precalificación."*), y el `<option>` vacío pasa de *"Elija el departamento"* a *"Elija primero el departamento"*. Es un **espejo** del `<select>` de Precalificación —sin `name`, nada nuevo al servidor— escrito por **la misma** cascada que llena las ciudades (`actualizarCiudades`). Ningún campo se movió: Ciudad sigue dentro del `<fieldset>` del alta (no editable antes de precalificar) y Precalificación conserva sus 4 datos de elegibilidad — la "zona" de la política es el **departamento**, no la ciudad. Ver `recorrido/FASE2_PLAN.md` § Clientes — tanda 2 | *(este commit)* | — |
| 14/11/2026 | **B155**: *"Dar de baja"* pasa de una fila desplegable dentro de la tabla a una **ventana modal** por fila (patrón de `mora/_resultados.jsp`, Bootstrap puro, sobrevive al reemplazo del fragmento por AJAX). El modal **es** la confirmación —título con el nombre del cliente, el efecto en una línea y *"Sí, dar de baja"*—, así que el formulario ya no lleva `data-confirmar`. Misma ruta, mismos campos, mismo CSRF; el *"Reactivar"* de la fila no cambió. Ver `recorrido/FASE2_PLAN.md` § Clientes — tanda 2 | *(este commit)* | — |
| 14/11/2026 | **B156**: el listado deja a la vista *"Cédula o nombre"* y *"Estado"*, y los otros **6** filtros pasan a un bloque plegable con el botón **"Más filtros (N)"** (Bootstrap `collapse`). El **servidor** decide si abre desplegado —cuenta cuántos de esos 6 vienen con valor en el `FiltroClientes` del request—, así que un filtro aplicado nunca queda escondido; el **número** lo mantiene el navegador porque `listado-vivo.js` no vuelve a dibujar la tarjeta de filtros, y el texto sigue siendo de la JSP (B92). Un campo oculto por `collapse` **igual se envía**: el filtrado, los informes y el listado vivo no cambian. Flecha por `aria-expanded` (`sgcpc-tema.css` §11). Ver `recorrido/FASE2_PLAN.md` § Clientes — tanda 2 | *(este commit)* | — |
| 14/11/2026 | **B157**: el listado ordena por **C.I., Apellidos y nombres, Teléfono y Estado** (las cuatro columnas que viven en `personas`); Ciudad, Situación laboral y Préstamos activos no ofrecen el control porque no salen de esa consulta. Ordena el **SQL** (la paginación es `LIMIT/OFFSET`: ordenar la página en el navegador daría un orden falso), con **lista blanca** clave → fragmento en `PersonaDAO` y validación en el controlador antes de guardar la clave. El orden viaja en `FiltroClientes`, así que el **informe PDF y el CSV salen en el orden de la pantalla**; la paginación, la búsqueda en vivo y los botones de informe lo conservan, y *"Limpiar"* limpia filtros sin perderlo. Todo orden que no sea el de defecto desempata por `apellidos, nombres`. Cubierto por `PersonaDAOOrdenTest` (12 casos, verificado contra una violación). Ver `recorrido/FASE2_PLAN.md` § Clientes — tanda 2 | *(este commit)* | — |

## 9. Pruebas realizadas en pantalla

**08/10/2026 (Joel), en el navegador**, en al menos 2 rondas documentadas en los mensajes de
commit: la de "6 mejoras de pantalla tras la prueba de Joel" (`7495062`) y la de "ajustes
finales de Precalificación (a-g), reprobado por Joel en el navegador" (`f86819a`, tag
`rf02-probado`). Las dos confirman que Joel vio la pantalla funcionando y pidió las
correcciones puntuales que quedaron reflejadas en esos mismos commits.

**Diferencia con el proceso actual**: estas pruebas fueron informales (confirmadas en el propio
mensaje de commit, sin un documento `RF02_clientes.md` con una sección "Pruebas realizadas en
pantalla" como la que tienen RF04 en adelante) — no hay un listado punto por punto de qué se
probó, solo la lista de correcciones que salieron de esa prueba. Es razonable asumir que lo que
no generó una corrección quedó "OK", pero no está documentado con el mismo detalle que RF04+.

**14/10/2026 (Joel), REPRUEBA FORMAL, perfil oficial, `sgcpc_db_pruebas`** — tag
`rf02-reprobado`: precalificación con 75 años → "fuera de política" (rechazo correcto); 35
años, departamento Central, asalariado → "Califica"; guardar el formulario vacío →
"Faltan datos obligatorios" con la lista completa; "Imprimir requisitos" con logo; alta de
Carlos Mancuello (C.I. 789.456, tecleada con puntos) → aceptada y guardada sin puntos, "se
registró correctamente". **Resultado: APROBADO, sin hallazgos nuevos.**

**16/10/2026 (Joel), normalización de `personas` — grupo Domicilio, usuarios oficial y gerente,
`sgcpc_db_pruebas`** — tag `norm-domicilio-probado`: Miguel Sosa (id 11) editado de
Central/Ñemby a Asunción/Asunción/Villa Morra/Av. Mariscal López 1234 — tras F5 el Departamento
mostró "Asunción" (derivado de la ciudad, nunca guardado); el filtro de la lista de Clientes por
departamento Asunción incluyó a Miguel y por Central no; el resumen de aprobación de su
solicitud (SOL-2026-0010) mostró "Asunción / Asunción" con la zona habilitada correcta, aprobada
por el gerente; formalizada → CC-2026-00031, contrato con "Av. Mariscal López 1234, Villa Morra,
Asunción"; domicilio cambiado después a Recoleta / Calle España 500 — el contrato ya emitido no
cambió (D-94) y el re-emitido mostró el domicilio nuevo. Verificado después por `SELECT`:
`personas` con 28 columnas, `personas_domicilio` con 1 fila por persona sin huérfanas, el
documento del contrato original quedó `REEMPLAZADO` (sigue guardado) y el re-emitido `VIGENTE`.
**Resultado: APROBADO, sin hallazgos nuevos sobre el formulario de Domicilio en sí** (los 5
hallazgos de usabilidad detectados durante esta prueba, ninguno específico de Domicilio, quedaron
anotados en `RNF_revision.md` §10).

**17/10/2026 (Joel), normalización de `personas` — grupo Laborales (ÚLTIMO GRUPO), base de
prueba limpia (ver `AVANCE.md` D-155), `sgcpc_db_pruebas`** — tag `norm-laborales-probado`: Jesús
Acosta (id 1) comerciante sin campos de empleador exigidos; cambio a Asalariado exige los 5 datos
del empleador, se guardan y se leen igual tras F5; vuelta a Comerciante, los campos desaparecen
sin exigirlos; creó y envió SOL-2026-0001 a aprobación, con afectación recalculada en el envío con
los datos actuales ((483.199 + 250.000) ÷ 5.700.000 = 12,9 %). Verificado después por `SELECT`:
`personas` con 15 columnas; 1 fila en `personas_laborales` por persona, sin huérfanas; Jesús con
`ingreso_neto_comprobado = 5700000`, `cuota_otras_deudas_mensual = 250000`; SOL-2026-0001 con
`ingreso_cliente_evaluado = 5700000`, `porcentaje_afectacion = 0.1286`. **Resultado: APROBADO**.
Dos hallazgos de esta misma prueba (Expediente "2 de 4" porque `COMPROBANTE_DOMICILIO` no bloquea
el envío a aprobación — ver `RF03_expediente.md §10` — y el aviso al cambiar situación laboral —
`RNF_revision.md §10f`) resultaron **preexistentes, no del grupo Laborales**; no bloquean este
cierre.

**29/10/2026, Joel (oficial Lorenzo Ramírez, 20:23-20:32) — B91, el aviso dice cuáles son los
datos congelados:**

- Ficha de *Ana María Prueba*, con `SOL-2026-0007` ya enviada a aprobación: los **dos** banners de
  ① Datos (el de Precalificación y el de Trabajo e ingresos) dicen *"**Los datos en gris** no se
  pueden modificar porque la solicitud SOL-2026-0007 está pendiente de aprobación. Si los datos del
  cliente cambiaron, informe al gerente para que la rechace, registrando el motivo."*, con los
  campos congelados efectivamente en gris y el resto editable.
- **El modal del servidor no se pudo ver y queda anotado como no visto**: por el camino normal de
  la pantalla no hay forma de dispararlo, justamente porque los campos grises no se pueden editar.
  Lo cubren 3 JUnit, uno de ellos verificando que ese texto **nunca** contenga la palabra "gris".

## 10. Pendientes y trabajo futuro

Falta la reprueba formal propia de este proceso ya está resuelta (ver sección 9, 14/10/2026,
tag `rf02-reprobado`).

- ~~**(09/10/2026, hallazgo de la unificación de documentación) Falta validar en el SERVIDOR el
  campo "Dirección (calle y número)"**~~ — **resuelto el 14/10/2026** (Tanda 2):
  `ServicioClientes.validarDatosTandaA` ya rechaza `direccionDomicilio` vacía, mismo patrón que
  el resto de "Tanda A". Cubierto por `ServicioClientesTest.testDireccionDomicilioVacioSeRechaza`
  (`mvn test` en verde) — **sin prueba en pantalla todavía** (no apareció en la reprueba formal
  del 14/10/2026 ni en la Tanda 2, Joel autorizó el commit solo con la cobertura de JUnit).
- **(13/10/2026, hallazgo del análisis de RNF14/rendimiento, Tanda 2 — anotar, no implementar)**:
  la búsqueda de Clientes (`PersonaDAO.construirWhere`) filtra con
  `translate(nombres) ILIKE translate(?)` (D-136, para ignorar tildes) combinado con `%texto%` —
  una función sobre la columna más un comodín al principio, que nunca puede usar un índice
  (tampoco existe ninguno hoy en `personas` más allá del `UNIQUE` automático de
  `cedula_identidad`, `02_indices.sql`). Siempre es un recorrido secuencial completo de la
  tabla. Con los pocos cientos de filas de hoy no se nota (medido dentro de RNF14: búsqueda de
  clientes 0,30-0,50 s, bien dentro de los 2 s que pide Doc. 00 §14.2) — queda anotado para si
  la base de clientes crece mucho con el tiempo.
- **(16/10/2026, hallazgo de Joel, prueba en pantalla del grupo Baja, usuario gerente) El aviso
  de cliente inactivo muestra el código interno del motivo, no el texto legible**: la ficha
  (`clientes/ficha.jsp` línea 173, "motivo: `<%= bajaFicha.getMotivoBaja() %>`") imprime el
  valor crudo de la columna (`PEDIDO_CLIENTE`, `DATOS_DUPLICADOS`, `FALLECIMIENTO`, `OTRO`) en
  vez de una etiqueta legible ("Pedido del cliente", "Datos duplicados", etc.) — mismo problema
  que ya se corrigió para la situación laboral (D-130, `ServicioClientes.etiquetaSituacionLaboral`).
  Sin corregir todavía.
- **(16/10/2026, hallazgo de Joel, misma prueba) El botón "Reactivar" casi no se ve**: dentro del
  aviso `alert-secondary` (gris) de cliente inactivo, el botón es `btn-outline-success` (texto
  verde sobre fondo gris) — poco contraste. Sin corregir todavía.
- **(16/10/2026, hallazgo de Joel, misma prueba) La columna de estado de la lista de clientes
  dice "Activa/Inactiva"**: `enumeraciones/EstadoPersona.java` define las etiquetas como
  `ACTIVA("Activa")`/`INACTIVA("Inactiva")`, usadas por `clientes/_resultados.jsp:93`
  (`fila.getEstadoPersona().getEtiqueta()`) para la columna/badge de estado. Para personas
  (clientes) el adjetivo correcto es "Activo/Inactivo" — distinto criterio que
  `usuarios.estado_cuenta` (`ACTIVA`/`BLOQUEADA`/`INACTIVA`, que sí es femenino, "la cuenta").
  Sin corregir todavía.
- **(16/10/2026, hallazgo de Joel) "Siguiente: ② Expediente" se vio con estilo deshabilitado**
  (relleno pálido, texto claro) en `/clientes/10` con usuario gerente, justo después de
  "Cliente reactivado." — con oficial en `/clientes/5` se ve normal. Investigado (sin
  reproducir visualmente, solo lectura de código): `procesarBajaOReactivar` hace un redirect
  real (PRG) a `GET /clientes/{id}` tras reactivar, así que `mostrarFicha` vuelve a leer la
  persona de la base — no hay ningún estado "viejo" guardado en sesión ni en caché que se
  reutilice entre el POST y el GET siguiente. El botón (`ficha.jsp:648`) solo se omite si
  `esNueva || !puedeVerDocumentosBuro`, y el `<fieldset>` que lo contiene (`ficha.jsp:303`) solo
  trae `disabled` si `esNueva` — ninguna de las dos depende del perfil ni de si la persona
  acaba de reactivarse. En la matriz de permisos (`ServicioAutorizacion.java`) el gerente tiene
  `TOTAL` en `REGISTRAR_EDITAR_CLIENTE` y en `CARGAR_DOCUMENTOS_CONSULTA_BURO`, igual que el
  oficial — no encontré, leyendo el código, ninguna razón para que el botón quede deshabilitado
  para gerente y no para oficial en una ficha existente y activa. **Causa no confirmada**: puede
  ser real (algo que no se ve desde el código, p. ej. algo específico del cliente 10) o puede
  ser una percepción visual (`btn-outline-primary` es intencionalmente más pálido que "Guardar
  cambios", que es `btn-primary` sólido — un botón "secundario" sin estar deshabilitado).
  Para confirmar cuál de las dos: la próxima vez, inspeccionar el elemento en el navegador
  (F12) y ver si el `<button id="botonSiguienteDesdeDatos">` tiene de verdad el atributo
  `disabled` (o lo tiene su `<fieldset>` padre) o si solo se ve pálido por su estilo. Sin
  corregir todavía.

## 11. Guion para la defensa

1. Abrir "Nuevo cliente" — mostrar la caja de Precalificación bloqueando el resto del
   formulario hasta completar los 4 datos.
2. Completar la precalificación con un caso que NO califica — mostrar el motivo en rojo.
3. Completar un caso que SÍ califica — mostrar que el resto del formulario se habilita.
4. Mostrar los documentos sugeridos cambiando la situación laboral (comerciante vs. asalariado).
5. Imprimir "Requisitos para solicitar un préstamo".
6. Guardar el cliente — mostrar el cartel único con todo lo que falta, si falta algo.
7. Editar un cliente ya existente que ya no califica — mostrar que SÍ se puede seguir editando
   (a diferencia del alta).
