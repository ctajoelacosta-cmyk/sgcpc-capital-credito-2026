---
name: sgcpc-diseno-ui
description: Usar al cambiar el aspecto visual de cualquier pantalla (.jsp) o documento (.jrxml) de SGCPC, con Playwright para las capturas antes/después y las skills design:accessibility-review (contraste y foco) y design:ux-copy (textos). Reglas propias del proyecto que esas herramientas no conocen — offline, AdminLTE local, significado fijo de colores, límite entre diseño y lógica, y portabilidad a otro servidor.
---

# Diseño de pantallas y documentos en SGCPC

Esta skill fija las reglas propias del proyecto. Usarla **junto con**:

- **`design:accessibility-review`**, para **medir** contraste y foco de lo que ya está
  construido (incluye el gris del `disabled` y el anillo verde de 4px de `GUIA_VISUAL.md` §4).
- **`design:ux-copy`**, para los **textos** — y lo que proponga pasa igual por las reglas de
  abajo ("usted", título + detalle, el botón que dice la operación real).
- **Playwright**, solo para las **capturas antes/después** de cada pantalla tocada, a 1280x800,
  guardadas fuera del repositorio (ver "Lista de control B86"). Habilitado por Joel el
  05/11/2026; la captura **registra, no prueba**.

**`ui-ux-pro-max` e `impeccable` NO se usan para cambiar el estilo** (decisión de Joel,
05/11/2026): el estilo del sistema ya está definido y probado en pantalla, y una propuesta de
paleta o tipografía de una skill externa lo que haría es rediseñarlo. Antes de esa fecha esta
skill las nombraba para "elegir estilo, paleta y tipografía" (`ui-ux-pro-max`) y para "revisar y
pulir" (`impeccable`); si alguna vez se las vuelve a usar para algo, sigue valiendo el límite ya
fijado: de `ui-ux-pro-max`, **únicamente** `scripts/search.py` y `scripts/design_system.py` (los
dos verificados sin ningún uso de red) y nunca `--persist` sin que Joel lo pida; de `impeccable`,
solo el texto de su `SKILL.md`/`reference/` (sus scripts nunca se instalaron acá).

Las reglas de abajo priman si hay conflicto con lo que cualquiera de esas herramientas sugiera,
porque son específicas de este sistema.

## Reglas fijas

- **Solo AdminLTE/Bootstrap/jQuery/SweetAlert2 ya vendorizados en
  `src/main/webapp/recursos/`** — nunca un CDN, nunca una fuente de Google Fonts en vivo, nunca
  nada que pida algo a internet. El sistema funciona sin conexión (confirmado en
  `docs/auditoria/AUDITORIA_TECNICA.md`, Área 6) y eso no cambia por una mejora visual.
- **Jerarquía clara**: un título por pantalla, subtítulos solo donde agrupan contenido real,
  nunca dos elementos compitiendo por la misma atención.
- **Espaciado uniforme**: mismo criterio de márgenes/padding entre tarjetas y secciones
  parecidas de todo el sistema — no un valor distinto por pantalla porque "se ve bien ahí".
- **Números alineados a la derecha, con separador de miles** (`1.806.750`, nunca
  `1806750` ni alineado a la izquierda) — en tablas, tarjetas y PDF por igual.
  - **La pieza es `.cc-monto`, y es la única** (B160, 16/11/2026, criterio de Joel):
    `text-align: right` + `font-variant-numeric: tabular-nums`, definida **una sola vez** en
    `sgcpc-tema.css` §16. **Nunca `text-right` de Bootstrap en una celda de cifras**: así estaba
    antes, pantalla por pantalla, y por eso el sistema tenía dos formas de decir lo mismo.
    `MontosAlineadosTest` frena la vuelta de la forma vieja.
  - **`tabular-nums` no es adorno**: la tipografía del sistema trae dígitos de ancho variable (el
    **1** más angosto que el **8**), así que sin eso los números **no se apilan en columna** aunque
    el borde derecho coincida — y apilarlos es para qué existe la alineación.
  - **Qué se alinea y qué no**: una **columna** de cifras de una tabla, **con su `<th>` y con el
    total de su `<tfoot>`** (si se alinea solo la celda, el encabezado queda corrido) — también en
    las tablas que viven dentro de una ficha. **Un monto suelto al lado de su etiqueta (par
    etiqueta/valor) NO se toca**, ni un importe metido en una frase (*"Promesa: 12/11 — 500.000
    Gs."*, *"N préstamos — saldo X"*, *"Aprobadas en el período: 5 (12.000.000 Gs.)"*), ni un
    rótulo de subtotal que va a la derecha siendo texto.

- **Una celda que se parte: `nowrap` o ancho mínimo, y la diferencia es medible** (B241-bis,
  16/11/2026).
  - **Primero se mide cuántas filas se parten de verdad**, con los rectángulos del texto y no a
    ojo: `const r = document.createRange(); r.selectNodeContents(celda); r.getClientRects().length`
    da los renglones que ocupa esa celda. El alto de la celda **no** sirve: es el de la fila, que
    puede estar alta por otra columna.
  - **Y se mide con los filtros puestos**, no solo en la pantalla inicial: el navegador reparte el
    ancho según lo que hay en la página, así que una columna que no se parte sin filtro se parte
    cuando el filtro deja solo rótulos cortos. En `/prestamos` el *Estado* se partía **1 de 20**
    sin filtro y **20 de 20** con *«En mora»*.
  - **`cc-sin-partir` (§13) va cuando el contenido es una unidad corta** — *"12 meses"*,
    *"154,6 %"*, un monto con su *"Gs."*.
  - **`min-width` (§14) va cuando en la misma columna conviven rótulos cortos y largos**: prohibir
    partir obligaría a la columna al ancho del más largo (*"Anulado antes del desembolso"*, 28
    caracteres, unos 210 px) y empujaría la tabla entera a la barra horizontal incluso cuando ese
    valor no está en la página. El mínimo sale del ancho medido del rótulo corto **más el relleno
    de `.table-sm`**, no de una estimación.
  - **Y se dice el costo con el número**: ensanchar una columna puede destapar la barra horizontal.
    En `/prestamos` la tabla pasó de 911 a 920 px en un contenedor de 912, o sea **8 px**. Esa barra
    es la salida que §13 declara aceptable, pero se informa, no se esconde.
  - **El TOTAL es el primero que se parte**, y por eso los pies se revisan aparte: es el número más
    largo de su columna y suele vivir en una celda angosta. Al tocar una columna de cifras, mirar
    **los tres lugares** donde puede haber un total: el `<tfoot>`, las **filas de total que NO
    están en un `<tfoot>`** (los dos resúmenes de Informes las tienen como un `<tr
    class="font-weight-bold">` suelto, y un inventario por `<tfoot>` no las ve) y las filas de
    **subtotal** intercaladas en el cuerpo (Morosidad).
  - **Y un literal cuenta como celda de esa columna**: `iva.jsp` imprime `<td>0 Gs.</td>` fijo en
    *Gravada 5%* e *IVA 5%* porque hoy son siempre cero. Ningún regex de getters lo encuentra, y sin
    alinearlo la columna queda a medias — y además *"0 Gs."* **también se parte** si la celda es
    angosta. Al relevar una columna, leer el marcado completo, no solo los `<%= %>`.
  - **Cuando el `<th>` sale de un arreglo de columnas** `{rótulo, orden, clase}` —Aprobaciones,
    Desembolso, Mora, Solicitudes—, la clase se declara **en el arreglo**, no con un `if` adentro
    del `<th>`: la pantalla dibuja, no decide (B88).
  - **Antes de alinear, mirar celda por celda**: contar con un regex da un **máximo**, no una
    orden. El inventario de B160 marcó como columna un *"fecha — monto"* que es texto.
  - **Y el humo raspa celdas**: su único patrón sobre un `<td>` leía el monto de `/desembolso` con
    `<td>` **exacto**, así que agregarle una clase lo rompía en silencio (habría dado *FALLO 0 vs
    2.000.000*). Antes de ponerle una clase a una celda: `grep -nE "<t[dh][ >]" pruebas/humo_http.sh`,
    y si hay que cambiar el patrón, **verificar que el nuevo suma lo mismo que el viejo sobre el
    HTML del día** antes de tocar el guion, y volver a medirlo después.

- **Un menú dentro de la barra lateral: la receta que quedó probada** (B240, 16/11/2026).
  - **El `dropup` de Bootstrap 4.6.1 alcanza solo, sin JavaScript nuevo**: ya se carga en
    `pie.jsp`, y da el **Esc**, el **clic afuera**, **Enter** para abrir, las **flechas** entre los
    renglones, el **`aria-expanded`** y **el foco que vuelve al disparador**. Antes de escribir un
    solo `addEventListener` para un desplegable, probar si Bootstrap ya lo hace.
  - **`data-display="static"` es lo que permite posicionarlo con CSS**: apaga el `applyStyle` de
    Popper, que le escribe un `transform` **en línea** al menú y le gana a cualquier regla. Sin
    eso no hay forma de abrirlo **al lado** cuando la barra está angosta.
  - **La barra lateral recorta, y en dos ejes distintos**: AdminLTE le pone a `.main-sidebar`
    `overflow-y: hidden` **siempre** y, colapsada, además `overflow-x: hidden`. Así que un menú
    que se sale de la barra necesita que el `overflow` ceda **solo mientras está abierto**:
    `…:has(.pie-usuario.show) { overflow: visible }`. **No se saca el `overflow: hidden`**: está
    para que nada escape de los 4,6 rem de la barra angosta.
  - **El disparador tiene que ser un `<button>` FUERA de `.sidebar`** si no se quiere que la barra
    se expanda al clickearlo: AdminLTE engancha su `focusin`/`focusout` a
    **`.main-sidebar .sidebar a`**, o sea solo a los enlaces de adentro del menú que scrollea.
  - **Si el texto del disparador se oculta con `display:none` en algún estado, el botón se queda
    sin nombre accesible** en ese estado. Se arregla con un `<span class="sr-only">` adentro que
    nunca se oculte; y conviene que el rótulo visible quede **contenido** en el nombre accesible
    (WCAG 2.5.3), que es lo que pasa al poner el `sr-only` primero y el texto después.
  - **"Nada clickeable invisible" no se verifica con un contador de tamaños**: un elemento adentro
    de un contenedor en `display:none` mide 0×0 y un contador ingenuo lo marca como "invisible"
    cuando en realidad **tampoco se puede clickear**. La prueba buena es `display` del contenedor
    + **`document.elementFromPoint`** en el lugar exacto: dice quién recibiría el clic de verdad.

- **Nada que escriba HTML va adentro de una etiqueta abierta** (16/11/2026, el defecto del botón
  *PDF* que Joel vio en *Informes → Caja*). Un `<%@ include %>`, o un `<%= %>` que devuelve
  marcado, puesto entre los atributos de un `<a>` o un `<button>`, **cierra esa etiqueta en su
  propio `>`**: los atributos que vienen después se convierten en el **contenido** del botón, y el
  usuario lee `class="btn ..." title="...">PDF` en otra tipografía y dentro de un recuadro. Pasó en
  5 de los 20 reportes, justo los que exportan con `<button>` de formulario.
  - **Lo único que puede ir adentro de una etiqueta es un `<%= %>` que devuelva atributos o un
    trozo de valor** (`class="btn<%= claseBotonExportar(...) %>"`, `<%= atributosBotonExportar(...) %>`).
    Un include, un `<% scriptlet %>` o un comentario van **antes** de la etiqueta, nunca en medio.
  - **`mvn test` no protege de esto**: el marcado roto es **JSP válido** y el `jetty-jspc` lo
    compila sin una queja. *BUILD SUCCESS* no dice **nada** sobre el HTML. La red que sí lo ve es
    **`EtiquetasHtmlDePantallaTest`**, que reconstruye el marcado servido de cada JSP y lo recorre
    con un autómata adentro/afuera de etiqueta: falla si un `class="`/`title="`/`href="` queda en
    posición de **texto visible**, y falla si una etiqueta tiene **otra adentro**.
  - **Antes de copiar un bloque compartido a una pantalla nueva, leer dónde dice que va**: el
    propio `plantilla/ayuda_exportar_reporte.jsp` traía escrito *“va justo ANTES de los dos
    botones”*, y los 15 reportes que exportan con `<a>` ya lo tenían bien
    (`reportes/cartera.jsp:31-33` es el modelo). El error no fue de criterio: fue pegar el bloque
    sin mirar si la etiqueta de al lado estaba cerrada.
  - **Y una pantalla que se ve bien no absuelve a las otras 19**: acá el síntoma era visible solo
    en 5, y a ojo se probó una. El inventario lo tiene que hacer una red, no la vista.
- **Colores con significado fijo, en todo el sistema**: verde = éxito/confirmado, amarillo =
  pendiente/esperando, rojo = error o acción irreversible, azul = acción principal. Nunca usar
  rojo para "simplemente destacar" ni verde para algo que todavía no se confirmó.
- **Un botón principal por pantalla** (el de la acción que se espera que la mayoría haga) — el
  resto, secundario o de texto.
- **Botones que dicen lo que hacen**, nunca genéricos: "Sí, aprobar" / "No, volver", nunca
  "Aceptar"/"Cancelar" a secas cuando hay más de una acción posible en la pantalla (ver B3 del
  recorrido — exactamente el problema que esto evita).
  - **El botón de descartar dice "No, volver" en TODO el sistema** (B3/B31 para el default de
    `plantilla/pie.jsp`; **B116**, 05/11/2026, para los `Swal.fire` escritos a mano). Lo cubre
    `TextosDePantallaTest`. **Con dos excepciones deliberadas, que son la regla de verdad**: si el
    cartel **no es una confirmación** sino informativo, "No, volver" promete una vuelta que no
    existe (el aviso de *"Cédula ya registrada"* dice **"Cerrar"**); y si el botón de descartar
    **hace algo**, se nombra lo que hace (el aviso de sesión por vencer dice **"Salir"**, porque
    cierra la sesión). Antes de uniformar el texto de un botón, preguntarse si ese botón **descarta
    o ejecuta**.
  - **Y "Cancelar" es peor en algunas pantallas que en otras**: en cobranza esa palabra ya significa
    *cancelación total del préstamo*, así que el botón de descartar se lee como el de una operación.
    Cuando una palabra genérica coincide con un término del negocio de **esa** pantalla, el problema
    no es de estilo: es ambigüedad real.
- **Confirmaciones con monto, cliente y efecto**: cualquier diálogo de confirmación de una
  operación debe mostrar el monto exacto, a quién afecta, y qué va a pasar — nunca solo "¿Está
  seguro?".
- **Mensajes de éxito con "qué sigue"**: nunca un mensaje de éxito que termine en sí mismo (ver
  B18/B22/B51 del recorrido) — decir qué puede o debe hacer el usuario después.
- **Textos en "usted"**, consistente con el resto del sistema ya escrito.

## Lenguaje del usuario (B85, 28/10/2026)

**Lo que el usuario lee se llama como el trámite, no como el estado interno del sistema.** El
caso que fijó la regla: la pantalla se llamaba *"Cierre documental"* y nadie entendía qué había
que hacer ahí; pasó a ser **"Devolución del pagaré"**, que es como ya la llamaba el menú.
Criterio: si un empleado nuevo no puede decir qué hace esa pantalla leyendo el título, el título
está mal.

Cuando se cambia un nombre visible hay que cambiarlo **en toda la ruta del trámite de una vez**
(menú, tarjeta del panel, sección del listado, botones, ficha, avisos emergentes y mensajes del
servidor) — buscándolos con `grep` sobre `src/main/java` y `src/main/webapp`, no de memoria.
Dejar la mitad con el nombre viejo es peor que no cambiar nada.

**Lo que NUNCA se renombra junto con el texto visible:**

- **ids y anclas** (`id="cierre-documental"`) y **las URL**: de ellos cuelgan los enlaces del
  menú y del panel, el script V3h de `pie.jsp` y el `scroll-margin-top` de `cabecera.jsp`.
- **Estados y columnas de la base** (`estado_cierre_documental`) y **operaciones de auditoría**
  (`COMPLETAR_CIERRE_DOCUMENTAL`, y los textos de detalle ya guardados): cambiar una etiqueta de
  auditoría parte la lista en dos vocabularios, porque las filas viejas conservan el texto viejo.
- **Los nombres de los informes** y cualquier palabra que venga del **anteproyecto firmado**
  (RF29 dice literalmente "cierre documental"). Renombrar un informe es una decisión de Joel,
  aparte.
- **Textos de otros RF ya consolidados** (tag `*-probado`): se listan y se le pregunta a Joel,
  no entran de arrastre.

## Un mensaje de cartel va en DOS oraciones (B42, 01/11/2026)

Los carteles del sistema (SweetAlert2, armados en `pie.jsp`) parten el mensaje en **título** y
**detalle** con `dividirEnTituloYDetalle`: buscan el primer `". "` seguido de mayúscula. Si no lo
encuentran, muestran el mensaje **entero como título** y le aplican `texto.replace(/\.\s*$/, '')`
— **a los títulos les quitan el punto final**, que es lo correcto para un título.

Consecuencia práctica, que costó una prueba en pantalla: el rechazo de B42 era una sola oración y
se veía **sin su punto**, aunque el punto estaba en el código. **Agregarle otro punto no arregla
nada**: el mismo `replace` se lo vuelve a comer.

- Todo mensaje que el usuario vea en un cartel (éxito, error, advertencia) se escribe como
  **frase corta + detalle**: *"Ese archivo ya está cargado. Es idéntico al que figura como «…»: no
  hace falta volver a subirlo."* El título queda sin punto (bien) y el detalle conserva el suyo.
- El detalle es el que puede ser largo y llevar comillas o dos puntos; el título tiene que
  entenderse solo, porque es lo único que se lee de reojo.
- Si el texto tiene un dueño en Java, el JUnit verifica **las dos cosas**: que termine en punto y
  que se parta en título y detalle (`TextosDePantallaTest`, con el mismo criterio que `pie.jsp`).
  Un test que solo mira el final del string no ve este defecto.
- Antes de dar por bueno un texto nuevo, **mirarlo en el cartel real**, no en el `String`. La
  misma trampa aplica a los `MensajesFlash` de advertencia y a los mensajes de error del
  servidor.

## Avisos y navegación guiada (B88/B89/B90, 29/10/2026)

Reglas que Joel fijó probando el circuito del oficial en pantalla. Valen para **cualquier**
pantalla de varios pasos, no solo para la ficha del cliente:

- **Un aviso nunca dice solo que algo no se cumple: nombra lo que falta.** *"No cumple
  validaciones preliminares"* pasó a *"No cumple validaciones preliminares. Falta: Comprobante de
  domicilio cargado."* Si el dato ya está en la misma página (una tabla de ✓/✗, un checklist), no
  hay excusa: se usa **ese mismo** texto, nunca uno redactado aparte.
- **Después de cada acción se lleva al PRIMER paso pendiente**, nunca a uno ya hecho ni a uno
  posterior si falta algo antes. Con una excepción que vale la pena recordar: mientras el usuario
  está **cargando documentos**, no se lo saca de esa pestaña por cada archivo — se le dice qué
  falta y se queda ahí.
- **Nunca un botón que lleva a la pestaña en la que el usuario ya está.** Parado en ②, el botón
  decía *"Siguiente: ② Expediente →"*. Si el paso pendiente es la pestaña actual, no va botón:
  las filas marcadas "Falta" ya guían.
- **Un enlace a otra ficha abre la pestaña del paso pendiente**, no siempre la primera — si el
  enlace se llama *"Ver ficha / expediente"*, tiene que abrir el expediente (ancla
  `#tabDocumentos`/`#tabBuro`). No aplica a los enlaces de listados e informes: ahí se entra a
  mirar un dato, no a seguir un trámite (decisión de Joel, 29/10/2026).
- **El texto y el destino los decide el servicio, no la JSP.** La JSP pregunta ("¿ofrecés botón
  acá?", "¿qué ancla?") y dibuja; si una pantalla empieza a decidir por su cuenta a dónde lleva,
  en dos semanas dice algo distinto de las demás. Ver "Un dato, un dueño" en `sgcpc-cambio-seguro`.
- **El orden de una lista de pasos es presentación, pero cambia la ayuda**: al mover la
  autorización del buró a la fila 1, hubo que corregir los textos que decían "fila 4" en pantalla
  y en el manual. Si una fila se nombra por su número, ese número es un dato duplicado.
- **Un aviso al lado de campos editables tiene que decir CUÁLES no se tocan** (B91, 29/10/2026).
  *"Estos datos no se pueden modificar"* estaba debajo de un bloque donde la mitad de los campos
  sí se edita: el usuario no sabe a qué se refiere. Los congelados ya se veían **en gris**, así que
  el aviso los nombra por ahí ("Los datos en gris…"), sin listar campos uno por uno — una lista en
  el texto sería otra copia que se desincroniza.
- **Antes de reescribir un texto, buscar en cuántos lugares se muestra.** El mismo mensaje salía
  en el banner de la ficha **y** en el modal de rechazo del servidor, donde no hay nada gris que
  señalar: cambiarlo a secas lo habría hecho mentir en uno de los dos. La salida no es duplicar el
  literal sino **un parámetro de contexto** sobre el método que ya lo arma (una redacción, dos
  arranques), con un test que cruce los dos y fije que solo pueden diferir en eso.
- **Un texto que solo aparece en un camino inalcanzable por pantalla se declara "no visto"** en el
  registro de la prueba, y se cubre con JUnit — nunca se da por probado porque "es el mismo
  método".
- **Si una frase la muestran la JSP y además el JavaScript, el dueño es el servidor** (B92,
  29/10/2026). El JS no puede llamar al servicio, así que la JSP le deja la frase ya armada en un
  `data-` del propio elemento, con `{n}` donde va el número, y el JS **solo reemplaza el número**
  — nunca redacta. Con una plantilla por forma (singular y plural) alcanza, y un JUnit comprueba
  que la plantilla, completada, dé **exactamente** la misma frase que el primer render. Antes de
  este patrón, el texto de "Por cantidad de cuotas" estaba escrito tres veces: una en la JSP y dos
  en el JS, que ya habían quedado distintas entre sí.
- **El verbo de una etiqueta puede mentir aunque el número esté bien.** *"Paga 1 cuota: 228.050"*
  invitaba a compararlo con la cuota del plan (289.470), que es otra cosa; *"Adelanta 1 cuota (sin
  intereses futuros)"* dice lo mismo sin inducir la comparación. Cuando un monto es "lo que cuesta
  una operación", el texto tiene que nombrar **la operación**, no el resultado.

## El número grande de una pantalla (B79/B81, 30/10/2026)

Reglas que Joel fijó probando la tarjeta de cobro, válidas para **cualquier** pantalla con un
"total" destacado:

- **El número más grande de una tarjeta es el que va a pasar, no el que se tecleó.** Si lo escrito
  no se puede ejecutar, ese número **no se muestra**: va un `—` y el motivo queda en el renglón de
  avisos. Mostrar *"Adelantar a capital: 3.500.000 Gs."* con un aviso rojo debajo que dice que ese
  monto no se puede cobrar es la pantalla contradiciéndose a sí misma.
- **Y si la operación es parcial, el número grande es el parcial**: *"Cobra 100.000 Gs. (pago
  parcial)"*, con lo exigible y lo pendiente en el renglón chico de abajo. Antes el total exigible
  (180.696) ocupaba el lugar grande y lo que se iba a cobrar (100.000) estaba dos renglones más
  abajo, más chico.
- **Un solo pintor.** Si varios lugares escriben el mismo elemento, cada uno sabe su pedazo y
  ninguno sabe el estado completo: se deja **un** método que recibe "qué se quiere decir" y decide,
  y los demás solo le pasan el dato. Acá eran cinco (la JSP y cuatro puntos del JS).
- **Un bloqueo tiene un solo renglón.** Si un motivo se escribe en otro lugar ("Vuelto", un aviso
  propio), el resto de la pantalla no se entera de que está bloqueada. Todos los motivos van al
  mismo renglón, y ese renglón es el que avisa al pintor.
- **Con la operación bloqueada, se ocultan también las piezas derivadas** (vista previa, desglose,
  "qué queda después"): si no, quedan mostrando el resultado **del monto anterior**, que es peor
  que no mostrar nada.
- **Nunca pegar un punto a un monto ya formateado**: `formatearGs`/`comoGuaranies` terminan en
  `"Gs."` y el resultado queda *"1.880 Gs.."*. Lo cubre `TextosDePantallaTest` (ver
  `sgcpc-cambio-seguro`, paso 15b).
- **Un aviso en gris no es un error.** "Falta que escribas algo" (gris) y "lo que escribiste está
  mal" (rojo) no pueden compartir el texto ni el color — y con el campo vacío **no va rojo**. Caso
  que lo fijó (B95, 30/10/2026): apenas aparecía el campo de la fecha de promesa, vacío y sin
  tocar, ya salía en rojo *"La fecha de promesa debe ser posterior a hoy"*. La causa no era el
  texto sino **un solo booleano** que habilitaba el botón y además decidía el aviso: si una
  variable contesta "¿puedo seguir?" y "¿está mal lo que escribió?" a la vez, uno de los dos
  mensajes va a mentir. Se parte en dos preguntas, y el pedido va al **renglón de motivos** que ya
  existe, no a un aviso propio al lado del campo.
- **El botón de un cartel de confirmación dice la operación real, no la opción elegida** (B96,
  30/10/2026). *"Sí, cobrar lo vencido"* era correcto en un cobro completo y falso en un pago
  parcial, donde el cliente paga menos: pasó a *"Sí, cobrar 100.000 Gs."*. Mismo criterio que B92
  para el verbo de una etiqueta, aplicado al botón que ejecuta.
- **Un escritor suelto en el JavaScript gana siempre: pisa la pintura correcta del servidor**
  (B98, 30/10/2026). La JSP ya dibujaba bien el rótulo, pero una línea del JS lo tenía **escrito a
  mano** y corría en **cada carga** de la pantalla (el script preselecciona la primera opción), así
  que el texto equivocado volvía medio segundo después — con Ctrl+F5 incluido. Antes de dar por
  arreglado un texto: buscar el literal en el `.js` con `grep`, no solo en la JSP. Y la guarda que
  lo cierra es prohibir **el literal** en el JS, no confiar en que nadie lo vuelva a escribir.
- **El test fija la frase que se lee, no la pieza.** El mismo caso: había JUnit del rótulo
  (*"Pagar la cuota de hoy"*) y pasaba en verde mientras la pantalla mostraba *"Pagar lo vencido:
  289.011 Gs."*, porque lo que el usuario lee es la **línea armada** (rótulo + monto) y eso no lo
  miraba nadie. Si un texto se compone de dos partes, el test compone las dos.
- **Un cartel de confirmación no redacta: repite.** Si el resumen ya dice una frase con dueño, el
  cartel usa **esa** frase (el mismo armador), y solo agrega lo que el resumen no tiene. Así la
  cajera confirma lo que acabó de leer, palabra por palabra.

## Un problema se avisa UNA vez (B123, anotado el 10/11/2026)

Al cerrar B122, la ficha de una solicitud en BORRADOR avisaba la capacidad superada **tres veces**:
el texto rojo en vivo debajo de *"Monto"*, el cartel amarillo del checklist (*"Falta: …"*) y el
cuadro rojo *"La cuota supera la capacidad de pago del cliente…"*. Los tres dicen lo mismo y dos
repiten los mismos dos números.

- **Antes de agregar un aviso, contar cuántos ya dicen eso** en la misma pantalla. Es el criterio
  de B87 ("si la pantalla ya lo dice, no se agrega otra copia") y de B107 ("una sola nota explica
  el porqué"), aplicado a los avisos.
- **Cuando hay varios, no se elige por gusto: se pregunta qué aporta cada uno.** Acá el de arriba
  es el único que sirve **mientras se escribe** el monto, el cartel es el único que lista **todo**
  lo que falta, y el cuadro rojo es el único que dice **qué hacer**. La decisión es de Joel y se
  toma **mirando la pantalla**, no el código.
- **Que los tres digan el mismo número no los justifica**: desde B122 salen del mismo dato, y aun
  así sobran dos. Coherencia y repetición son problemas distintos.

## El límite que no se cruza

**No cambiar lógica, cálculos ni datos.** Esta skill (y las externas) solo tocan `.jsp`/`.jrxml`
en su aspecto — texto estático, clases CSS, orden visual, color, espaciado. Cualquier cambio que
afecte un `<%= %>` con una cuenta, una condición de negocio, o un dato que viene de la base, no
es un cambio de diseño: es un cambio de código (`sgcpc-cambio-seguro`) o de documentos con datos
fiscales (`sgcpc-reportes-jasper`), con su propio protocolo.

## Portabilidad (el sistema se entrega como .war + base, para instalarse en el servidor de la institución, en otra máquina, sin internet)

- Nunca una ruta absoluta de esta computadora (`C:\Users\...` ni ninguna otra).
- Nunca un recurso de internet (CDN, Google Fonts en vivo, cualquier fuente externa) — solo lo
  ya vendorizado localmente.
- Las fuentes de los documentos PDF viajan dentro del `.war` (dependencia `jasperreports-fonts`,
  ver `sgcpc-reportes-jasper`) — nunca dependen de una fuente instalada en el servidor destino.
- `.claude/` (skills, settings, incluidas estas) nunca viaja en el `.war` — queda fuera de
  `src/main/webapp`, la única carpeta que empaqueta `maven-war-plugin` (confirmado en
  `pom.xml`).

## B86 — la mejora de UX/UI pendiente es de TODO el sistema

Pedido explícito de Joel (28/10/2026, `FASE2_PLAN.md` área B): hay **una pasada de UX/UI
pendiente sobre todas las pantallas**, no sobre una. Va **después de las correcciones
prioritarias** y se hace con esta skill, en una sola tanda, con el alcance y el orden acordados
con Joel antes de empezar (alcance ya acordado el 03/11/2026 — ver "Lista de control B86" abajo,
que además fija que las skills externas **no** se usan para cambiar el estilo).

**Mientras tanto, no se arregla por pantalla suelta**: si aparece un defecto visual de este tipo
(controles con el estilo gris por defecto del navegador, botones pegados sin espacio, contenido
cargado a la izquierda con vacío a la derecha, botones deshabilitados que no se leen como
deshabilitados — **B87**), se **anota como parte de B86** y se sigue. Un parche local en una
pantalla es justamente lo que esta pasada viene a evitar, y además contradice §13.5 de
`REGLAS_DESARROLLO.md` (nada de soluciones temporales).

## Lista de control B86 (pasada visual)

**Alcance aprobado por Joel el 03/11/2026**, en cuatro tramos y en este orden: **transversal**
(~~B87 estilo `disabled` único~~ **HECHO el 05/11/2026**, B105 foco de los carteles que mueven
dinero, B4/B43 confirmaciones genéricas) → **cobranza** (B106 rótulos a mano en el JS) → **caja y
cierre** (B61, B52, B53, B107)
→ **documentos** (B12 hueco del contrato, B59 espacios del recibo, B108 ancho del nombre de la
cajera), con los **PDF al final** porque cada uno exige muestra, recompilar su `.jasper` y una
línea del manifiesto. **FUERA de B86**: **B97** (no es visual, toca la lógica de los 4 listados en
vivo — descartado para esta versión) y **C21** (criterio de agrupamiento de un informe, no
diseño). **EN PAUSA desde el 03/11/2026**: primero va la documentación para la mesa. El
prerequisito está cumplido —`pruebas/humo_http.sh` en **1015/1015** el 03/11/2026— pero **se
vuelve a correr justo antes de empezar**: una foto vieja no sirve para comparar.

Los diez puntos de abajo **no son el tramo**: se corren **completos y de la misma manera en cada
pantalla que se toque**, sea del tramo que sea. El número entre paréntesis es el ítem del
`FASE2_PLAN.md` que fijó la regla, no un permiso para arreglar solo esa pantalla.

1. **Botón deshabilitado: un único estilo en todo el sistema** (B87). **HECHO el 05/11/2026**
   (`sgcpc-tema.css` §2b) — queda acá como el modelo a copiar para el resto del tramo 1, no como
   pendiente. Lo que dejó escrito:
   - **La causa raíz no estaba en la JSP sino en qué regla gana en el navegador.** Bootstrap **y**
     AdminLTE repiten el color de cada variante **para el estado apagado**
     (`.btn-primary.disabled,.btn-primary:disabled{background-color:#007bff}` y sus hermanas),
     además del `opacity:.65` general. O sea: el deshabilitado **volvía al azul genérico de
     Bootstrap**, más vivo que el azul de marca — *"Registrar cliente"* apagado llamaba **más** la
     atención que un botón activo. Antes de pulir cualquier estado visual (`:disabled`, `:hover`,
     `:focus`, `.active`), **grepear ese estado en el CSS vendorizado** y ver qué está pisando qué;
     leer la JSP no alcanza.
   - **Un gris plano, no una opacidad** (corrige lo que decía este punto hasta el 05/11/2026, que
     mandaba reusar la opacidad **.45** de `GUIA_VISUAL.md` §4). Medido: con `.45` el rótulo queda
     en **1,85:1** de contraste y no se lee **qué** botón es; el gris plano `#64748B` sobre
     `#F1F5F9` da **4,34:1**. §4 fijó `.45` para el **ítem de menú sin permiso**, que es un
     `<span>` de una palabra; un botón lleva una frase. Lo demás del lenguaje de §4 **sí** se
     reusa: gris, `cursor: not-allowed` y el motivo en el `title`.
   - **`opacity: 1` se declara a propósito**, nunca se omite: sin declararlo, el `opacity:.65` de
     Bootstrap sigue aplicándose encima y se lleva puesto el contraste (y además apaga el borde y
     los íconos).
   - **`fieldset:disabled button.btn`/`input.btn` van aparte** del `:disabled` propio, nombrando
     el elemento. **Corregido el 10/11/2026 (B109)**: hasta esa fecha acá decía
     `fieldset:disabled .btn` y que *"el botón puede no llevar el atributo (lo hereda)"*. Medido en
     pantalla, ese `<button>` **sí** matchea `:disabled` aunque lo herede del fieldset, así que
     `.btn:disabled` ya lo cubría; se los nombra para que el caso quede a la vista, no porque haga
     falta.
   - **Un `<a class="btn">` encerrado NO se pinta como deshabilitado** (B109, 10/11/2026). No es un
     control de formulario, el fieldset no lo deshabilita y nunca matchea `:disabled`: lo único que
     le pasaba era que Bootstrap y AdminLTE le mataban el clic
     (`a.btn.disabled,fieldset:disabled a.btn{pointer-events:none}`), y eso dejó **muerto el
     "Volver" del alta de cliente** — la única salida de la pantalla. §2b ahora trae la excepción
     `fieldset:disabled a.btn{pointer-events:auto}`, que gana por orden de carga, sin `!important`.
     **Si un control vuelve a estar vivo, tiene que verse vivo**: el color correcto se **mide**
     clonando el elemento fuera del contexto roto (`cloneNode` + `getComputedStyle` + sacarlo) y
     después se comprueba que el real dé los mismos valores. `a.btn.disabled` se deja intacto: ahí
     el apagado lo pide la pantalla, no el fieldset.
   - **Nombrar `:hover`, `:focus` y `:active` explícitamente**: en Chrome `:hover` **sí** matchea
     un `<button disabled>`, así que sin eso el hover de la variante sigue corriendo sobre un botón
     apagado — que era justo lo que lo hacía parecer tocable.
   - **Sin `!important`**: `sgcpc-tema.css` se carga en `cabecera.jsp` **después** de
     Bootstrap/AdminLTE y los selectores empatan en especificidad (0,2,0), así que gana por orden.
   - **El motivo tiene una sola clase** (`.motivo-deshabilitado`) y **el texto sigue siendo de cada
     pantalla**: la clase fija cómo se ve, no qué dice. Se aplica a los motivos que **ya existen**;
     no se agregan textos nuevos "de paso".
   - **Un `title` fijo SOLO en los botones que deshabilita el servidor.** Si lo habilita el
     JavaScript (*"Cobrar"*, *"Imprimir requisitos"*, *"Registrar consulta"*, *"Confirmar
     desembolso"*), un `title` estático **miente apenas el botón se habilita** — el navegador lo
     sigue mostrando. Esos heredan el estilo y conservan su renglón de motivos en vivo.
   - **Si la pantalla ya dice el motivo, no se agrega otra copia**: `aprobaciones/ficha.jsp` ya lo
     dice **tres veces** (alerta roja, `title`, glosario ⓘ) y no se tocó.
2. **Los carteles que mueven dinero o emiten documentos abren con el foco en "Cancelar"** (B105).
   **HECHO el 05/11/2026** — queda acá como el modelo para el resto del tramo 1, no como
   pendiente. Un Enter de más no puede confirmar un cobro. El mecanismo **ya existía**
   (`data-confirmar-foco="cancelar"` en `plantilla/pie.jsp`, que es `focusCancel` de SweetAlert2) y
   el modelo a copiar fue *"Entregar efectivo a la caja"*. Lo llevan: **cobrar, adelantar a
   capital, cancelar todo, desembolsar, entregar efectivo a la caja, abrir caja, cerrar caja,
   solicitar y autorizar reversión, y generar/regenerar/re-emitir documentos**. Es una opción
   **por cartel**, no un cambio global. Lo que dejó escrito:
   - **Los carteles de confirmación son DOS familias, no una**, y el `grep` del atributo solo ve
     la primera: los `form[data-confirmar]` que arma `plantilla/pie.jsp` (ahí va el atributo) y el
     **`Swal.fire` propio de `recursos/js/cobranza-ficha.js`**, que no pasa por `pie.jsp` y lleva
     `focusCancel: true` escrito ahí mismo. Es justo el que cubre **cobrar, adelantar a capital y
     cancelar todo el préstamo**, o sea los tres caminos que más plata mueven. Antes de dar por
     completo un inventario de "todos los carteles de X", **buscar el mecanismo (`Swal.fire`,
     `showCancelButton`), no el atributo** — el atributo solo existe donde ya se aplicó.
   - **Al grepear por SweetAlert2, excluir lo vendorizado**: `grep -rn "focusCancel"
     src/main/webapp/` devuelve 43 KB de `recursos/sweetalert2/dist/sweetalert2.all.min.js`
     (la librería declara todas sus opciones en una línea). Acotar con `--include=*.jsp
     --include=*.js` y filtrar `recursos/sweetalert2|recursos/plugins|recursos/adminlte|
     recursos/bootstrap|recursos/jquery`.
   - **Un cambio "por cartel" se demuestra fotografiando también un cartel que NO cambió.** La
     tercera captura de B105 es *"¿Bloquear a «cajera»?"* abriendo en "Sí": sin ese control, dos
     capturas con el foco en "No, volver" no distinguen un cambio por cartel de uno global que
     pisó todo el sistema. Vale para cualquier cambio que se declare acotado.
   - **Media pasada visual puede ser infotografiable por el estado del sistema, no por la
     pantalla.** Cobrar y desembolsar mostraron *"No hay caja abierta"* y **no renderizaron el
     formulario**; regenerar y re-emitir los PDF se esconden si el préstamo ya tiene los firmados
     cargados. Ninguna de las cuatro se podía capturar sin **abrir una caja o formalizar**, que es
     armar el escenario para la foto (prohibido, ver "Herramientas de la pasada"). Se declaran
     **"no vistas"** en el registro del ítem y se dice **qué estado haría falta**, para que Joel
     las mire cuando ese estado exista de verdad. Que Tomcat sirva el archivo nuevo
     (`curl … | grep`) confirma el despliegue, **no** el comportamiento.
   - **El criterio no es solo "mueve dinero o emite un documento": también "no se deshace"**
     (ampliación de Joel al probar B43, 05/11/2026). *"Rechazar"* una solicitud no mueve plata ni
     emite nada —por eso quedó fuera de B105— pero una solicitud RECHAZADA **no vuelve a
     pendiente** (`enviarAAprobacion` exige BORRADOR), así que también abre en "No, volver". Antes
     de decidir el foco de un cartel, preguntarse las **tres** cosas: ¿mueve dinero?, ¿emite algo
     numerado?, ¿se puede volver atrás? Y verificar la tercera en el código —qué método podría
     devolver el estado— en vez de suponerla.
   - **Un paso que emite un documento puede no tener cartel ninguno**, y entonces no es un ítem de
     foco sino de confirmación nueva: *"Generar la constancia de devolución"*
     (`cierre-documental.jsp`) era un `<form>` pelado que emitía `DEV-xxxx-xxxxxx` sin preguntar
     nada. Quedó como **B112** y se **resolvió el 10/11/2026** con el mismo mecanismo de
     `pie.jsp`, sin tocar Java. Lo que dejó escrito su texto:
     - **Lo irreversible no siempre es lo que parece.** El servicio es **idempotente** (un segundo
       clic no numera de nuevo), así que el cartel no dice "no se puede deshacer": dice que el
       **número y el nombre impreso** ya no se cambian desde esa pantalla. Antes de redactar la
       advertencia, leer el método y ver **qué** queda fijo de verdad.
     - **El cartel nombra a quien va a quedar impreso en el documento.** La constancia sale a
       nombre de quien la genera (B78) y ese aviso ya estaba arriba del botón: el cartel lo
       **repite donde se decide**, no lo reemplaza.
     - **Un dato que ya está en otro campo no se repite**: el N.º de pagaré es igual al
       identificador de operación (verificado en la base), así que no entra al resumen.
     - **La fecha que va en el cartel es la que la pantalla ya muestra**, no la que recalcula el
       servicio por otro camino. Al inventariar "los carteles de X", listar primero **las
     acciones** y después ver cuáles tienen cartel: empezar por los carteles existentes esconde
     justamente a las que no lo tienen.
3. **Toda confirmación resume qué va a pasar** (B4/B43): **cliente, monto, plazo y el documento o
   número que se emite**. Nunca un *"¿Está seguro?"* pelado ni un *"Generar documentos"* sin decir
   cuáles. Y **un cartel no redacta: repite** la frase que ya armó su dueño en Java (ver "El número
   grande de una pantalla"), palabra por palabra, agregando solo lo que el resumen no tiene.
   **HECHO el 05/11/2026** (generar/regenerar documentos, aprobar y rechazar) — queda acá como el
   modelo a copiar, con lo que costó averiguar:
   - **Un cartel de varios datos necesita los saltos de línea DECLARADOS.** SweetAlert2 pinta
     `text` con `textContent` y su contenedor viene con `white-space: normal`, así que los saltos
     se colapsan en un párrafo centrado. La salida fue la clase `cartel-resumen`
     (`sgcpc-tema.css` §8: `pre-line` + `text-align: left`), que `plantilla/pie.jsp` agrega **solo
     si el mensaje trae saltos** — los otros 23 `data-confirmar` del sistema salen idénticos. En la
     JSP el salto se escribe `&#10;` **dentro del atributo, en una sola línea de código**: un salto
     real en el fuente también entraría al texto. Nunca pasar a `html:` para esto — con `text` el
     escapado lo hace el navegador.
   - **Un valor que recién se conoce al confirmar va en SU PROPIO atributo**
     (`data-confirmar-valor-<clave>`), y la JSP deja `{clave}` en la frase. Es la regla de B92
     aplicada al cartel: el JS escribe un **valor**, jamás una frase.
   - **Ese valor se lee con `getAttribute`, NUNCA con `.data()` de jQuery.** jQuery **cachea** el
     atributo en la primera lectura: si el valor viviera en `data-confirmar`, el cartel mostraría
     el dato viejo **desde la segunda apertura**. Por eso la prueba en pantalla de un cartel con
     valores vivos es **abrir, cancelar, cambiar el campo y reabrir** — la primera apertura no
     distingue nada.
   - **Antes de prometer un número en una confirmación, ver CUÁNDO se asigna.** *"Generar
     documentos"* no puede nombrar el N.º de operación: lo asigna
     `generarIdentificadorOperacion` dentro de la transacción, al confirmar, y consume numeración.
     El cartel dice que la operación *recibe* su número. En *"Regenerar"* sí existe y va en el
     título.
   - **Si la acción hace cosas distintas según el estado del formulario, el texto lo dice o se
     parte en dos plantillas.** `regenerarDocumentos` solo recalcula el plan y re-emite los PDF
     **si la fecha cambió**; un *"se reemplazan"* pelado habría mentido en la mitad de los casos.
   - **Lo que el cartel no puede repetir, no lo inventa.** A *"Aprobar"* no se le puso un "qué
     sigue" porque esa frase tiene dueño en Java y el servlet solo la expone con la solicitud ya
     aprobada: repetirla exigía tocar el servlet, y redactar otra habría abierto un segundo
     vocabulario para lo mismo.
4. **Los textos los arma el servidor/JSP, nunca el JavaScript** (B106, con B79/B92/B98). La JSP
   deja la frase ya armada en un `data-` del elemento y el JS **solo reemplaza el número**. Antes
   de dar por bueno un texto: buscar el literal con `grep` en `recursos/js/`, no solo en la JSP —
   un escritor suelto en el JS pisa la pintura correcta del servidor en cada carga de la pantalla.
   **HECHO el 05/11/2026** (B106, tramo 2) — queda acá como el modelo a copiar. Lo que dejó
   escrito:
   - **El inventario de copias de un rótulo se grepea también sobre `src/main/java`.** El ítem
     decía "dos rótulos en el JS"; «Todo el préstamo» tenía **seis** copias visibles, y dos eran
     **mensajes del servidor** que mandan a esa opción (*"Use «Todo el préstamo»"*,
     *"Elija «Todo el préstamo»"*). Un rótulo de opción no vive solo en la pantalla que lo dibuja:
     vive también en cada mensaje que le dice al usuario "andá a esa opción". Grepear la frase
     sobre **todo** `src/main` —`.java`, `.jsp` y `.js`— antes de declarar cuántas copias hay.
   - **Un texto visible puede no tener dueño en el servidor en absoluto.** «Adelantar a capital»
     —el nombre de la operación en RF27 y en el recibo— existía **únicamente** en
     `cobranza-ficha.js`. Al mudar un texto no se asume que haya un dueño que lo reciba: puede
     haber que **crearlo**, y entonces el ítem "visual" toca un `.java` (una constante de texto no
     es lógica, pero sí dispara el riesgo del Clean and Build — ver `sgcpc-cambio-seguro`).
   - **El respaldo del `data-` no repite la frase.** `plantillaDelResumen(nombreData, respaldo)`
     usa el respaldo con la pantalla vieja en caché; si ese respaldo es la frase completa, el
     literal sigue en el archivo y **la guarda del test no puede prohibirlo**. Se pone la forma
     mínima (`'Sí, cobrar'`, `'Adelantar'`), nunca la frase. Es el único punto donde B98 había
     quedado a medias: su `'Sí, cobrar la cuota de hoy'` sigue en el JS por eso.
   - **Y la guarda no puede depender de las mayúsculas.** Prohibir *"Todo el préstamo"* no alcanza
     si en el mismo archivo quedó *"Sí, cobrar todo el préstamo"* en minúscula: el test pasa en
     verde con la copia a la vista. Si al cerrar el ítem queda una variante con otra caja, o entra
     también o la guarda está mintiendo — decidirlo explícitamente, no dejarlo al azar.
   - **Las etiquetas de auditoría se quedan afuera, a propósito.** `EtiquetasAuditoria` conserva su
     propia copia de *"Adelantar a capital"*: renombrar una etiqueta de auditoría junto con el
     texto visible parte la lista en dos vocabularios, porque las filas viejas conservan el viejo
     (ver § "Lenguaje del usuario"). Al unificar un rótulo, esa copia **no** es un hallazgo: se
     declara como excepción deliberada en el registro del ítem.
5. **Cada monto dice de qué es** (B107): **aplicado a la deuda** vs. **efectivo en caja**. El
   01/11/2026 el panel mostró 844.004 (aplicado) y la caja 844.000 (efectivo) y la diferencia de
   4 Gs. es el redondeo a favor del cliente (D-141/D-142): los dos números están bien, lo que
   faltaba era la etiqueta. Es cambio de **texto**; el cálculo no se toca nunca.
   **HECHO el 07/11/2026** — queda como el modelo a copiar, con lo que dejó escrito:
   - **La etiqueta dice QUÉ es el número; una sola nota dice POR QUÉ pueden diferir.** Repetir la
     explicación en cada rótulo lo vuelve ilegible, y ponerla en los dos lados la convierte en dos
     copias que se desincronizan. Va en **la pantalla donde el usuario hace la comparación** —acá,
     la ficha de caja, donde la cajera cuenta su plata— y las demás solo se nombran bien. El
     dueño de la explicación es una pantalla, no todas.
   - **Antes de etiquetar un monto, verificar de dónde sale de verdad.** Las tres cifras del panel
     salían de `sumarAplicadoEntreFechas` (`monto_aplicado`) y el de la caja de los movimientos:
     recién con eso confirmado la etiqueta puede afirmar "aplicado a la deuda". **Una etiqueta que
     nombra mal la fuente es peor que no tener etiqueta**, porque ahora el número miente con
     autoridad.
   - **Y revisar si alguna etiqueta ya lo decía**: *"Efectivo en caja"* no se tocó. Agregarle
     "(efectivo)" habría sido ruido.
5b. **Un estado que se lee por AUSENCIA no se lee** (B61, 07/11/2026). La lista de cajas cerradas
   escribía *(revisada)* y nada más, así que la ausencia cubría **dos** casos distintos — "no hace
   falta revisarla" y "falta que la revisen" — y no había forma de distinguirlos. Lo que dejó
   escrito:
   - **Antes de elegir el texto, averiguar cuántos estados hay de verdad, en el código.** Acá
     parecían dos (revisada / no revisada) y eran **tres**, porque solo una diferencia distinta de
     cero se revisa: lo dicen el `WHERE` de la consulta que alimenta la bandeja del gerente y la
     validación que **rechaza** revisar una caja sin diferencia. Con dos estados el texto habría
     quedado mintiendo en el tercio más común (las cajas que cierran justas).
   - **Al estado que no necesita acción no se le pone texto si la pantalla ya lo dice** por otro
     lado (acá, la columna "Diferencia" con `0 Gs.`): agregarlo sería otra copia del mismo hecho
     (criterio de B87). Lo que hay que escribir es **el estado que falta**, no los tres.
   - **Y buscar la pantalla que ya lo resuelve bien**: el Informe de Caja imprimía esa misma
     columna con "Sí"/"No" desde siempre. Copiar el criterio de un lugar ya probado cuesta menos
     que inventarlo y no abre un segundo vocabulario.
   - **El estado nuevo puede no existir en los datos del día.** Verificado en la base: había **0**
     sesiones con diferencia sin revisar, así que el par de capturas salía idéntico y no probaba
     nada. Antes de sacar el "después" de un estado nuevo, **contar en la base cuántas filas lo
     tienen** — si son cero, hay que pedirle a Joel que arme ese estado o declararlo "no visto".

6. **Fechas con día y hora siempre que pueda haber más de un día** (B53) — el detalle de
   movimientos del arqueo mostraba solo la hora. Y **nunca "−0 Gs."** (B52): un monto cero no lleva
   signo. Los dos salen del formateador, no de un arreglo local en la plantilla.
7. **PDF: nada se corta ni queda un hueco grande en blanco** (B12, B59, B108). JasperReports **no
   recorta: descarta o corta en seco** —por eso se leyó *"Firma de la cajera (Sofía Martínez"* sin
   cerrar el paréntesis—, así que un ancho se **mide contra el dato más largo real de la base**, no
   se estima. Los PDF van **al final** de la pasada: muestra, recompilar el `.jasper`, línea del
   manifiesto, y lo que ya está emitido no se toca (ver `sgcpc-reportes-jasper`).
8. **Se respeta el estilo actual: se pule, NO se rediseña.** **La paleta vigente es la de B110**
   (aprobada en pantalla el 05/11/2026): fondo `#EEF3F8`, barra superior azul de marca con franja
   verde, recuadros con borde y franja verde arriba, tablas con encabezado azul marino, y las
   tarjetas del Panel de color pleno con tonos joya. **El dueño del dato es `GUIA_VISUAL.md` §1**
   (paleta, tonos del Panel, movimiento) — cualquier color nuevo se agrega **ahí primero**, nunca
   suelto en un `.jsp`. Manda también el resto de la guía (tipografías, radios, sombras,
   significado de los colores) y lo ya vendorizado de AdminLTE en `recursos/`. **Sin CDN ni nada
   de internet**, sin fuentes de Google en vivo, sin rutas absolutas de esta máquina.

9. **No usar `ui-ux-pro-max` ni `impeccable` para cambiar el estilo** (decisión de Joel,
   05/11/2026). El estilo del sistema ya está definido y probado en pantalla: una propuesta de
   paleta o tipografía de una skill externa lo que haría es rediseñarlo. Esto **restringe** lo que
   decían la cabecera de esta skill y la fila B86 del `FASE2_PLAN.md`, que las nombraban como parte
   de la pasada.
10. **Solo visual, como siempre**: ningún cambio de Java, servlets, permisos, rutas, cálculos ni
    datos en el mismo paso (ver "El límite que no se cruza" y `GUIA_VISUAL.md` §7.1). Si un punto
    de esta lista exige mover lógica, es un paso aparte con el OK de Joel.

### Herramientas de la pasada

- **`design:accessibility-review`** para **contraste y foco** (incluye el gris del `disabled` del
  punto 1 y el anillo verde de 4px de `GUIA_VISUAL.md` §4). Se usa para **medir**, no para
  proponer otra paleta.
- **`design:ux-copy`** para los **textos** (puntos 1, 3, 5, 6). Lo que proponga pasa igual por las
  reglas de arriba: "usted", dos oraciones (título + detalle, B42), botón que dice la operación
  real y no la opción elegida (B96).
- **Playwright** para las capturas **ANTES y DESPUÉS de cada pantalla tocada**, a **1280x800**
  (`browser_resize` y después `browser_take_screenshot`), guardadas en **`..\pruebas\b86_capturas\`
  — fuera del repositorio**, como el resto de `pruebas/` y `evidencias/`. **Esto cambia la decisión
  previa de "sin control de navegador" de esta skill** (Joel, 05/11/2026): el navegador se usa para
  **registrar** el antes/después, nunca para dar un cambio por probado. El par de capturas es para
  que Joel compare; **DOGMA 1 sigue intacto: la captura de Playwright no es la prueba** — la prueba
  es Joel en pantalla, con el circuito real.

  **Lo operativo, aprendido en B87 (05/11/2026) — leer antes de sacar la primera captura:**

  - **Captura de viewport, NUNCA `fullPage`.** Con la barra lateral `fixed` de AdminLTE, el
    `fullPage` la dibuja **encima** del contenido y la captura sale inservible. Si lo que hay que
    mostrar no entra en 800 px, se sacan **dos** capturas con `window.scrollTo` y un sufijo que
    diga qué muestra (`…_pasos2y3.png`), **en la misma posición** en el antes y en el después.
  - **`browser_run_code_unsafe` pierde el viewport**: hay que volver a fijar 1280x800 **dentro**
    del mismo bloque (`page.setViewportSize`). Si no, el par no es comparable — en B87 salió una
    captura de 1533x675 al lado de una de 1280x800.
  - **Playwright solo escribe dentro del proyecto.** Las capturas nacen en `.playwright-mcp/` (en
    `.gitignore` desde el 05/11/2026) y **de ahí se copian** a `..\pruebas\b86_capturas\`. Un
    `filename` que apunte fuera de la raíz lo rechaza.
  - **Pero si la sesión pasó por `browser_run_code_unsafe`, la captura cae en la RAÍZ del repo**,
    no en `.playwright-mcp/` (B106, 05/11/2026). Ahí **no la cubre el `.gitignore`**, así que
    aparece en `git status` y se puede colar en un commit. Después de cada captura: buscarla
    (`find . -name "B1xx_*" -newermt "-10 minutes"`), **moverla** a `..\pruebas\b86_capturas\` y
    confirmar con `git status --short` que el repo quedó limpio.
  - **Dentro de `browser_run_code_unsafe` no hay `require`**: `require('fs')` tira
    `ReferenceError: require is not defined`, así que no se puede leer un archivo (p. ej. el
    `JSESSIONID` que dejó el script de login) desde ese bloque. El valor se pasa **inline** en el
    código del bloque — es un id de sesión efímero, no una contraseña, así que no viola la regla de
    no escribir secretos en un comando; la contraseña sigue saliendo solo de `~/.sgcpc_prueba`.
  - **Entrar al sistema sin escribir la contraseña en ningún lado**: un script que la lee de
    `$HOME/.sgcpc_prueba`, hace el POST a `/acceso` con su `tokenCsrf` y devuelve **solo el
    `JSESSIONID`**; ese valor se le pasa al navegador con `context().addCookies(...)`. Es además la
    forma de **cambiar de perfil** (oficial → cajera) sin cerrar sesión ni retipear nada. En
    `cabecera.jsp` el campo es `nombreUsuario` y el token va en un `<input name="tokenCsrf">`.
    - **Con el MCP de Playwright, la cookie se pone con `browser_run_code_unsafe`** (16/11/2026):
      es el único que da acceso al objeto `page`, y de ahí a `page.context().addCookies([...])`.
      **`document.cookie` NO sirve**: la cookie de sesión es `HttpOnly`, así que el navegador
      ignora la escritura en silencio y la página sigue redirigiendo a `/acceso` — se probó.
      La cookie va con `{name:'JSESSIONID', value, domain:'localhost', path:'/sgcpc'}`.
    - **Y ese `page` es además la forma de medir de verdad**: `page.keyboard.press('Escape')`,
      `page.focus(...)` y `elementFromPoint` dentro de un `page.evaluate` prueban el teclado y qué
      recibe un clic. **Cuidado con los clics "en un punto vacío"**: desde B235 la tarjeta del
      Panel es clickeable entera, así que un clic de prueba en el medio de la pantalla **navega** y
      la medición se cae con *"Execution context was destroyed"*.
  - **Ctrl+F5 después de tocar un `.css`.** `VersionDespliegue.VALOR` (el `?v=`) solo se recalcula
    al **redesplegar**: Tomcat ya sirve el archivo nuevo, pero el navegador tiene el viejo.
    Conviene confirmarlo con `curl … | grep` sobre el recurso servido antes de capturar el después.
  - **Verificar con `getComputedStyle`, no a ojo**, que el estilo nuevo esté realmente aplicado
    (`backgroundColor`, `color`, `opacity`, `cursor`) — una captura linda sobre una caché vieja es
    exactamente el tipo de "prueba" que el DOGMA 1 rechaza.
  - **Mirar solamente**: ningún POST, ningún botón que genere o confirme. Si para llegar a una
    pantalla hay que abrir caja, cargar un archivo o emitir algo, **esa pantalla se declara "no
    vista"** en el registro del ítem — no se arma el escenario solo para la foto.


### Lo que dejó la revisión final de pantallas (11/11/2026) — leer antes de la próxima pasada

Pasada completa de **mirar**, 5 tandas por perfil, 70 capturas en `..\pruebas\revision_final\`,
17 hallazgos (**B126–B142**, sección **K** de `FASE2_PLAN.md`). Lo que conviene no volver a
aprender:

- **Una captura es la foto de un instante, no un estado.** La primera foto de `/clientes/{id}`
  salió diciendo *"Complete la precalificación para continuar."* en una ficha completa: la
  pantalla pregunta al servidor (`fetch` a `/clientes/precalificar`) y repinta **después**. Y el
  botón de al lado **parecía** deshabilitado y no lo estaba (es `btn-outline-secondary`, gris
  también cuando está vivo). **Antes de anotar un hallazgo de estado, medirlo**
  (`disabled`, `getComputedStyle`) y, si hay `fetch`, capturar **las dos** fotos: el destello y la
  resuelta. Quedó como B134.
- **Cambiar de perfil sin tocar la pantalla de login**: un script que lee `~/.sgcpc_prueba`, postea
  a `/acceso` con su `tokenCsrf` y devuelve el `JSESSIONID`; el valor se le pasa al navegador con
  `context().addCookies(...)` y se limpia con `clearCookies()` entre perfiles. Los cuatro usuarios
  son `SGCPC_USUARIO` (**gerente**), `_CAJERA`, `_OFICIAL` y `_ADMIN` (administrador).
- **Una sola llamada de `browser_run_code_unsafe` puede recorrer y capturar 20 pantallas** (bucle
  con `goto` + `setViewportSize(1280x800)` + `screenshot({path:'.playwright-mcp/…'})`). Sale mucho
  más barato que navegar y capturar de a una. **El viewport se vuelve a fijar en cada vuelta**, y
  las capturas se mueven después a `..\pruebas\` con `git status --short` de control.
- **`page.evaluate` que devuelve objetos grandes inunda la sesión**: devolver **una línea de
  resumen** (contadores, "scrollWidth vs clientWidth"), nunca el JSON de todos los elementos.
- **Lo que se puede mirar sin romper la regla de "ningún POST"**: los informes filtran con
  `method="get"`, así que un `?desde=…&hasta=…` muestra la tabla con datos sin escribir nada. El
  cotizador y "Nueva solicitud" son POST: se declaran **no vistos**.
- **Tres mediciones que valieron más que mirar**: `scrollWidth` contra `clientWidth` (el monto que
  se sale de su tarjeta, B126), `Range.getClientRects().length` sobre la celda (el código partido
  en dos renglones, B128: 199 de 199 filas) y `table.scrollWidth` contra `contenedor.clientWidth`
  (la columna TOTAL fuera de la vista en el informe de Cobros).
- **Barrer una familia entera, no una pantalla**: el hallazgo más útil (B129) salió de listar los
  **23** `data-confirmar` de todas las JSP y marcar cuáles tienen `-titulo` y `-foco`. Para que el
  regex no se rompa, hay que **sacar antes los scriptlets** (`<%…%>` y `${…}`): traen `>` adentro
  y cortan el `[^>]*` a la mitad.

## Lo que dejó escrito B110 (05/11/2026) — leer antes de tocar color o movimiento

B110 fue el rediseño de la paleta que Joel pidió **a propósito y por separado de B86**. Ya está
hecho y probado; lo que queda son las reglas que valen para el próximo cambio de aspecto:

- **Un color no se asigna por posición, se asigna por significado.** La maqueta de Joel pintaba
  las tarjetas con `.row:nth-of-type(n) > div:nth-child(m)` y eso **solo sirve para una foto**: el
  Panel arma filas distintas para cada perfil, así que la misma casilla le toca a tarjetas
  distintas y el color cambia de significado según quién entre. En el sistema, cada tarjeta lleva
  **su clase propia** en la JSP y el tono viaja con ella. Antes de dar por buena una asignación de
  color: leer las condiciones de visibilidad de la JSP **perfil por perfil** y comprobar que
  dentro de un mismo bloque ningún perfil vea dos elementos del mismo tono. Un tono se puede
  repetir solo entre elementos que **nunca se ven juntos** o en bloques distintos.
- **El contraste se mide en el peor estado, no en reposo.** Si el hover lleva
  `filter: brightness(k)`, el fondo se aclara y el blanco se lee peor: **ese** es el número que
  hay que medir. Dos tonos de B110 pasaban en reposo (5,02:1 y 4,76:1) y caían a 4,42:1 y 4,19:1
  en hover. Lo mismo vale para el extremo **claro** de un degradado: se mide ahí, no en el medio
  ni en el extremo oscuro.
- **Un `!important` solo lo pisa otro `!important`, por más orden de carga que haya.** La barra
  superior necesitó dos (`background-color` y `border-bottom`) porque la regla V4 del `<style>`
  de `cabecera.jsp` ya los declara. Antes de escribir una regla nueva: `grep` del selector en el
  `<style>` de `cabecera.jsp` **y** en el CSS vendorizado, no solo confiar en que
  `sgcpc-tema.css` va último.
- **Nunca un selector universal sobre una barra o un contenedor que tenga algo desplegable
  adentro.** `.main-header.navbar *{color:#fff}` habría dejado el menú del usuario con **letra
  blanca sobre fondo blanco**: ese menú cuelga de la misma barra. Se nombra elemento por
  elemento, y después se verifica con `getComputedStyle` que el desplegable siga oscuro.
- **Antes de pelear por especificidad, contar.** El enlace del pie de la tarjeta lo pintaba
  `.content-wrapper a:not(.btn):not(.nav-link):not(.page-link)` — cuatro clases más el elemento.
  Un selector corto no le gana nunca. La salida limpia fue **agregarle la clase propia a esa
  misma forma** (`.content-wrapper a.panel-tarjeta-enlace:not(.btn):not(.nav-link):not(.page-link)`),
  no un `!important`.
- **Una maqueta puede traer un defecto de interacción, no solo colores.** La de B110 incluía
  `cursor: pointer` en toda la tarjeta: la tarjeta entera **no es cliqueable** (lo es el enlace
  "Ir a…" del pie) y varias tarjetas no tienen enlace. Joel estuvo de acuerdo en sacarlo. Cuando
  una maqueta trae una afordancia, verificar que el elemento **haga** lo que la afordancia promete.
- **Todo movimiento nuevo lleva su `@media (prefers-reduced-motion: reduce)`**, en el mismo
  cambio. Se apagan transiciones y desplazamientos; la sombra y el brillo se pueden dejar, son
  color y no movimiento.
- **Al cambiar el color de una clase, revisar si alguna clase vieja quedó sin uso.** B110 dejó
  `.panel-tarjeta-ambar` y `.panel-tarjeta-verde` sin ninguna tarjeta que las llevara; se
  borraron (con el OK de Joel, porque estaban en una sección consolidada) y el comentario que las
  describía se corrigió. Una regla CSS que no matchea nada es documentación que miente.
- **Para ver el CSS nuevo en Playwright no alcanza `page.reload()`**: hay que limpiar la caché por
  CDP (`Network.clearBrowserCache` + `Network.setCacheDisabled`). El `?v=` de
  `VersionDespliegue` solo se recalcula al redesplegar, así que Tomcat sirve el archivo nuevo y
  el navegador sigue con el viejo — la primera tanda de verificaciones de B110 salió con los
  valores de antes. Siempre confirmar con `getComputedStyle` **antes** de sacar la captura.


## Mover una pieza de lugar es borrar la vieja, no dejarla (B111, 05/11/2026)

El usuario conectado y sus dos acciones pasaron de la barra superior al pie del menú lateral.
Lo que dejó escrito, para cualquier pieza que se mueva de sitio:

- **Mover = agregar en el destino Y borrar en el origen, en el mismo cambio.** Acá el nombre
  estaba en **dos** lugares (la tarjeta de arriba del menú y el desplegable de la barra
  superior); si se agrega el destino sin sacar los dos orígenes, el dato queda tres veces. Antes
  de escribir el destino, listar **todos** los lugares donde ya aparece.
- **Al borrar marcado, borrar también su CSS**, y comprobarlo con `grep` de cada clase sobre
  todo `src/main/webapp` (cero menciones). Una regla que no matchea nada es documentación que
  miente — mismo criterio que B110 con `.panel-tarjeta-ambar`/`-verde`.
- **Y borrar el CSS de lo que el bloque arrastraba.** Con el desplegable del usuario se fueron
  las reglas de `.main-header .dropdown-menu*`: era el **único** desplegable de la barra
  superior, así que quedaban sin marcado aunque no nombraran ninguna clase del bloque. Preguntar
  "¿qué más dejó de existir al sacar esto?", no solo "¿qué clases tenía?".
- **Ocultar con `display:none`, no con opacidad, lo que además es un botón.** La tarjeta vieja
  escondía su texto en la barra colapsada con `opacity:0; width:0` y eso estaba bien para un
  texto; para un botón **no**: un control invisible por opacidad **se sigue pudiendo clickear**,
  y acá uno de los dos es *"Salir"*.
- **"Fijo abajo aunque el menú sea corto"** sale de hacer el `<aside>` una columna flex: el que
  scrollea (`.sidebar`) se lleva el alto sobrante (`flex:1 1 auto`), el logo y el pie no se
  encogen (`flex:0 0 auto`). Con el menú largo el pie baja debajo de la última opción en vez de
  taparla.
- **Con la barra colapsada hay que recalcular márgenes y padding, no solo esconder texto.** La
  barra angosta mide 4.6rem (74px): con el margen y el padding de la tarjeta expandida, un
  círculo de 40px no entra. Se verifica midiendo (`getBoundingClientRect`) que el borde derecho
  de la pieza quede **dentro** del borde de la barra, en los dos estados y en los tres perfiles.
- **Reusar el dato que ya existe antes de calcular uno nuevo**: las iniciales salieron de
  `inicialesUsuario`, que la barra superior ya calculaba (primera letra de nombres + primera de
  apellidos). Ninguna lógica nueva, ninguna consulta nueva.
- **Al rehacer una pieza, corregir el hallazgo de contraste que esa pieza tenía anotado.** El
  círculo de iniciales arrastraba un `#1B9B6A` con blanco en 3,53:1, registrado al cerrar B110 y
  no tocado entonces porque no era parte de aquel ítem. Acá, que la pieza se reescribe entera, el
  cambio salió gratis (`#167F57`, 4,99:1).
- **Un borde también se mide.** El contorno de un control entra en WCAG 1.4.11 y pide **3:1**,
  no 4,5:1: el `rgba(255,255,255,.25)` de la maqueta daba 2,1:1 sobre la tarjeta y pasó a `.45`
  (3,6:1). El texto se mide con 4,5:1 y el borde con 3:1 — son dos cuentas distintas.
- **Un enlace que se saca de un desplegable pierde el foco que el desplegable le daba**: hay que
  devolverle un `:focus-visible` propio (el anillo verde de `GUIA_VISUAL.md` §4) y comprobar con
  el teclado que entre en el orden de tabulación y se vea.

## Un control que "falta" en una pantalla puede estar puesto por un script compartido (B216, 15/11/2026)

El informe de UX de Seguridad dijo que */cambiar-contrasena* tenía *"tres campos sin mostrar/ocultar"*.
Era falso: `pie.jsp:34` carga `recursos/js/campo-contrasena.js`, que le agrega el botón del ojo **y**
el aviso de Bloq Mayús a **todo** `input[type="password"]` de la página, sin que el campo necesite
ninguna clase ni atributo. El informe se escribió leyendo la JSP — donde, efectivamente, no hay
ningún botón.

- **Al revisar una pantalla, el marcado de su JSP no es todo lo que la pantalla tiene.** Antes de
  anotar que un control falta, mirar los scripts que `pie.jsp` carga para **todas** las pantallas, y
  en particular los que actúan **por tipo de elemento** y no por clase: hoy
  `campo-contrasena.js` (`input[type="password"]`), `campo-guaranies.js` (`.campo-guaranies`) y
  `campo-cedula.js`. Los dos últimos piden una clase, así que se ven en la JSP; el primero **no**,
  y por eso es el que engaña.
- **La comprobación cuesta un `grep`**: `grep -n "recursos/js/" plantilla/pie.jsp` y, por cada
  script, su `querySelectorAll` del final. Si el selector es un tipo de elemento, ese
  comportamiento ya está en la pantalla que se está revisando.
- Es la misma familia que **B209** (contar la decoración en vez del mecanismo) y se arregla igual:
  el número o la afirmación equivocada **se corrige en la fila del ítem al cerrarlo**, diciendo qué
  decía el informe y qué era cierto. Un informe con fecha no se reescribe, pero el ítem que sale de
  él no puede heredar el error.
- **Y el ítem no se cae por eso**: la otra mitad (el aviso en vivo de que la confirmación no
  coincide) sí faltaba. Cuando un ítem tiene dos partes y una no existía, se dice cuál y se hace la
  que queda.

## El buscador en tabla busca solo en la página visible (B226, anotado el 15/11/2026)

`recursos/js/buscar-en-tabla.js` (V11) filtra **las filas que ya están en el DOM**, a propósito: no
manda ningún pedido. Eso estuvo bien mientras las pantallas que lo usaban mostraban todo de una vez.
Hoy **8 pantallas** lo combinan con paginación —los 7 reportes que paginó B210 y el historial de
parámetros desde B219—, así que buscar algo que está en la página 5 contesta *"Sin coincidencias."*:
un control diciendo que no hay algo que sí hay.

- **Antes de agregarle paginación a una pantalla que tiene el buscador en tabla**, hay que saber que
  el buscador queda acotado a la página. No es excusa para no paginar (paginar resuelve un problema
  mayor), pero **se anota**: hoy es **B226**, pendiente de decisión — o el buscador dice su alcance,
  o filtra del lado del servidor.
- **Y no se arregla en una pantalla sola** (B151): son 8, y la decisión de cuál de las dos salidas se
  toma es de Joel.

## Agrupar las filas de una tabla larga: el grupo sale del dato, no de una lista nueva (B220, 15/11/2026)

Los 52 parámetros eran una tabla plana imposible de recorrer. La agrupación **ya existía en el
dato**: la clave es `grupo.nombre` (`credito.monto_minimo`) y la consulta ya ordena por clave, así
que alcanzó con dibujar un encabezado cuando cambia el prefijo — ninguna consulta nueva, ningún
orden nuevo, ninguna lista de grupos que mantener.

- **La pregunta antes de agrupar**: *¿el orden actual ya deja juntos los de cada grupo?* Si sí, el
  encabezado es una fila más y nada más. Si no, agrupar es también cambiar el orden, y eso es otro
  ítem (con su comparador y su JUnit, como B221).
- **El nombre legible del grupo va en el dueño del dato**, no en la JSP (acá
  `ServicioParametros.grupoDeClave`), y un grupo sin nombre **cae en el nombre técnico** en vez de
  romper o de esconder la fila. Lo que avisa que falta un nombre es un JUnit que lee la lista real
  (`bd/03_parametros.sql`), no el ojo.
- **El encabezado va en un `<th colspan>` con `bg-light`**, dentro del `<tbody>`, y si la pantalla
  tiene buscador en tabla hay que marcarlo para que no lo cuente como dato (ver
  `sgcpc-cambio-seguro`, B220).

## Un corte "pasado / futuro" vale más que un filtro por año (B221, 15/11/2026)

La pestaña de feriados arrancaba por los **10 ya pasados** de los 24 cargados. El informe ofrecía
dos salidas —ordenar con los próximos primero, o cortar pasado/futuro— y resultaron ser la misma: el
orden pone adelante lo que sirve, y el corte lo explica. Un filtro por año habría agregado un
control que hay que mantener (y elegir) para contestar una pregunta que nadie hace dos veces.

- **Lo que define el orden de una lista de fechas de administración es si la fecha ya pasó**, no la
  fecha en sí: primero lo que viene (del más cercano al más lejano), después lo que pasó (del más
  reciente al más viejo).
- **El de hoy va con los que vienen.** Es el borde que hay que probar, y el que decide si el
  comparador recibe `hoy` como parámetro (sí: si no, el test depende del día en que se corra).

## "Que entren en una línea": se mide el UMBRAL de ancho, no se tantea el tamaño (B220-bis, 16/11/2026)

Joel pidió que las nueve píldoras de `/parametros` entraran en un renglón "con tamaño o padding un
poco menor". La tentación es apretar un poco, mirar la pantalla y decir que ya está. Medido en el
navegador salió otra cosa: con el tamaño nuevo necesitan **1.095 px** y el área de contenido a
1280x800 es de **927 px**, así que a 1280 **no entran** y van a seguir sin entrar; entran **desde
1.536 px de ventana**, que es justo el ancho de la pantalla donde él vio el problema.

- **Las tres mediciones que contestan la pregunta de una vez**: la suma de los anchos de los
  elementos más los `gap` (lo que **necesitan**), el `clientWidth` del contenedor (lo que **hay**),
  y la cantidad de valores distintos de `offsetTop` (**cuántos renglones** quedaron). Con eso se
  sabe el déficit exacto en píxeles, en vez de "parece que ahora sí".
- **Y se barre el umbral con un bucle de anchos** (1280, 1366, 1440, 1536, 1600, 1920): el
  resultado útil para Joel no es "lo arreglé", es *"entran desde 1.536; de 1.440 para abajo bajan
  parejas"*. Si el déficit no se puede cerrar con un tamaño razonable, **eso también es el
  resultado**, y la alternativa (acortar rótulos) es una decisión de él, no del arreglo.
- **El estado recordado arruina la medición siguiente.** La primera corrida dejó la píldora *"Caja"*
  en `sessionStorage` y la segunda falló con `element is not visible` al tocar un botón de una fila
  filtrada: un `TimeoutError` que parecía un defecto del producto y era el escenario sucio. Antes de
  medir: `sessionStorage.clear()` y recargar. (De paso quedó demostrado que el grupo **se recuerda**
  entre recargas, que era justo la funcionalidad pedida.)
- **Una variante de tamaño para una pantalla no se hace tocando el CSS de la otra**: va con una
  clase propia en el contenedor y selectores de **dos** clases, y conviene contar la especificidad
  **y el orden de carga** antes de escribirlos — acá el archivo del índice se carga *después* del
  tema, así que una sola clase perdía (B109).

## Toda una tarjeta clickeable: estirar el enlace que ya existe, no envolver en un `div` (B235, 16/11/2026)

La forma obvia —un `div` con `onclick` alrededor de la tarjeta— es la que deja al teclado afuera: no
recibe foco, no se alcanza con Tab y `Enter` no hace nada, y después se arregla con un `tabindex` y
un `keydown` que hay que mantener. La forma que quedó no toca la JSP:

```css
.panel-tarjeta { position: relative; }
.panel-tarjeta-enlace::after { content: ""; position: absolute; inset: 0; z-index: 1; }
```

- **El que recibe el foco sigue siendo el `<a>` de siempre**, así que el teclado funciona **por
  construcción**. Y como el área la da el enlace, **una tarjeta sin enlace no se vuelve
  clickeable**: no hay nada que estirar. Eso vale doble en este sistema, donde hay tarjetas que un
  perfil ve y no puede accionar.
- **El foco se dibuja sobre la TARJETA** (`.panel-tarjeta:focus-within`), no sobre el renglón del
  enlace: lo que se ve enfocado tiene que ser lo que `Enter` va a activar. Si el área clickeable
  crece y el recuadro de foco no, el usuario de teclado ve un objetivo de 13 px y activa uno de 120.
- **Una propiedad moderna se usa midiéndola y degradando**: `:has()` (para limitar el cursor de mano
  a las tarjetas con enlace) se verificó en el navegador de la máquina con
  `CSS.supports('selector(:has(*))')`, y está escrito en el CSS que, si no estuviera, **lo único que
  se pierde es el cursor, nunca el clic**. Lo mismo con `inset: 0`, que lleva las cuatro propiedades
  sueltas delante.

### Y cómo se mide esto sin engañarse

- **Playwright se NIEGA a clickear un elemento tapado** (*"`<a …>` intercepts pointer events"*). Con
  un área estirada eso **no es una falla: es la prueba de que funciona**. Para medirlo como lo hace
  una persona, se clickea en **coordenadas** (`page.mouse.click(x, y)`), no con el localizador del
  elemento que quedó debajo.
- **Sin `waitForNavigation`, `page.url()` devuelve la página vieja.** Las tres primeras mediciones
  de esta tanda dijeron que ningún clic navegaba, y lo único que pasaba era que el dato se leía
  antes de tiempo — mientras la pestaña ya estaba en `/cobranza`. Cualquier medición de "¿navegó?"
  va envuelta en `Promise.all([page.waitForNavigation(), …])`.
- **El caso negativo se mide igual que el positivo**: clic en una tarjeta **sin** enlace y confirmar
  que la URL **no** cambió, con una espera explícita. Sin ese par, "toda la tarjeta es clickeable"
  no distingue entre lo que debe navegar y lo que no.

## Antes de dar por terminado un cambio visual

Mostrar **antes y después** (captura o descripción de ambos) y pedirle a Joel que confirme con
su propia captura en pantalla — un cambio de aspecto se aprueba viéndolo, no leyendo el diff del
HTML. **DOGMA 1: una vista previa no es una prueba** — muestra el diseño, no el circuito que lo
produce.

## Un `clamp()` está calibrado para un dato, y el dato crece (B126, 11/11/2026)

`.panel-tarjeta-numero-monto` ya tenía `clamp(20px, 1.8vw, 28px)` **con el comentario que
prometía** *"tamaño adaptable (no se desborda ni pasa a dos líneas con números grandes)"*.
Era cierto cuando se escribió, con la cartera en 8 dígitos; con 9 el número se salía 10 px
de la tarjeta. El `vw` escala con la **pantalla**, no con la **caja**, así que la promesa
dura hasta que el dato crece.

- **Un tamaño adaptable se re-mide con el dato de hoy y con el dato de mañana.** Acá se
  probó a 1280, 1366, 1536 y 1920, con 9 **y con 10 dígitos** (se le cambia el `textContent`
  al elemento en el navegador y se compara `scrollWidth` con `clientWidth`). Elegir el
  coeficiente a ojo no sirve: la diferencia entre 1,35vw y 1,8vw no se ve hasta que se mide.
- **Las container queries (`cqi`) resolverían esto de raíz y NO se usaron a propósito**: si
  el navegador del servidor de la institución no las soporta, la declaración entera se
  descarta y el número vuelve al tamaño grande — o sea, al defecto. Con `vw` el peor caso es
  un número un poco más chico.
- **Contar "celdas partidas" con `Range.getClientRects().length` da falsos positivos**
  (B128): si la celda tiene un `<a>` más los saltos de línea del JSP, el rango incluye esos
  nodos de espacio y devuelve 2 rects aunque el texto esté en **una** línea. Se mide sobre
  **el elemento que lleva el texto** (el `<a>`), no sobre el `<td>`.
- **Una tabla de muchas columnas tiene un piso que no se baja con CSS** (B128): la del
  informe de Cobros, con 13 columnas, pide 953 px contra 912 aun con 13 px de letra y el
  padding al mínimo. Cuando se llega a ese piso, la salida ya no es de estilo sino de
  contenido (sacar el *"Gs."* de cada celda y ponerlo en el encabezado, ≈168 px) y **es
  decisión de Joel**. Conviene decirle el número que falta, no "no se puede".

## El peor ancho no es el más chico (B143, 11/11/2026)

Al calibrar un `clamp()` (ver la sección de B126) la tentación es medir en la pantalla **más
angosta** y dar por hecho que de ahí para arriba sobra lugar. En AdminLTE **no es cierto**:
por debajo de 992 px la barra lateral se colapsa y le **devuelve** el ancho al contenido.
Medido en el Cotizador, barriendo de 992 a 1440 px de a 32 con el peor dato posible, la caja
del número mide **83 px a 992, 77 px a 1024** y recién vuelve a crecer desde ahí.

- **Se barre el rango entero**, no dos o tres anchos elegidos a mano. El mínimo real puede
  estar en el medio.
- **Cuando a un ancho el dato no entra a ningún tamaño legible** (acá, 10,2 px de letra: el
  piso de B128), la salida no es bajar más la letra: es **cambiar la disposición** a partir de
  ese punto (las tres cifras van en fila recién desde 1200 px, `col-xl-4`, y debajo se apilan).
  Eso corre el peor ancho real al primer ancho donde la fila existe.
- **Para saber cuánto margen queda hay que medir el ancho NATURAL del texto**, con un clon en
  `width:max-content`, buscando el px más grande que entra. `scrollWidth` contra `clientWidth`
  avisa del **desborde** —es lo que pide B126— pero con un elemento de bloque que llena su caja
  da iguales siempre que entre, y entonces **no dice si falta 1 px o sobran 20**.
- **Y una tabla que se muda a una columna más angosta se vuelve a medir.** `.tabla-informe-ancha`
  tenía escrito que las fichas *"están bien con el tamaño actual"*: era cierto a ancho completo,
  no en los 589 px de la columna del resultado. El tamaño correcto depende **del ancho que la
  tabla tenga**, no de qué tabla sea.

## Fotografiar un cartel sin romper la regla de "ningún POST" (B129, 11/11/2026)

Abrir un cartel **no envía nada**: el `submit` lo intercepta `plantilla/pie.jsp` y muestra el
SweetAlert2 con `preventDefault`. Entonces:

- Se hace clic en el botón que lo abre, se lee el cartel (`.swal2-title`,
  `.swal2-html-container`) y se cierra con **Escape**. Nunca se toca *"Sí, …"*.
- **Si el formulario tiene campos obligatorios** (el motivo de la cancelación, el de la
  anulación), se completan **en el navegador** antes de apretar: sin eso salta el aviso *"Falta
  un dato"* de `pie.jsp` y uno cree que el cartel no existe. Llenar un campo no es un POST.
- **El foco se mide, no se mira**: `document.activeElement.textContent` dice en qué botón abrió
  el cartel. Es la única forma de demostrar un `data-confirmar-foco`.
- **Y se fotografía también un cartel que NO cambió** (B105): acá, *"¿Bloquear a «cajera»?"*
  abriendo en "Sí, bloquear". Sin ese control, tres capturas con el foco en "No, volver" no
  distinguen un cambio por cartel de uno global.

## Un contorno de color claro NO sirve para un rótulo (B133, 11/11/2026)

Para que *"Inactivar"* dejara de parecer más inofensivo que *"Bloquear"*, el primer intento fue
ámbar con contorno para el más leve y ámbar sólido para el más grave. **Medido, el contorno deja
el rótulo en 1,46:1** sobre el fondo blanco de la fila: el peor contraste de la pantalla. El
sólido, que lleva texto oscuro sobre el ámbar, da **5,05:1**.

- **La variante `outline` hereda el color de la variante como COLOR DE TEXTO.** Con azul o rojo
  eso alcanza (11,57:1 y 4,87:1, medidos en B131); con ámbar o verde claro, no. Antes de elegir
  un `btn-outline-*` para un rótulo, medirlo — no es una decisión de estilo, es legibilidad.
- **Cuando dos acciones son la misma clase de cosa, comparten color y se distinguen por el
  rótulo.** Bloquear e inactivar restringen el acceso y las dos se deshacen con "Reactivar":
  inventarles una jerarquía de peso visual costaba contraste y no agregaba información.
- **Un rótulo más largo cambia la tabla.** *"Restablecer contraseña"* (en vez de
  *"Restablecer"*) hizo que la columna "Acciones" pase a tres renglones. Al alargar el texto de
  un botón que vive en una celda, mirar la fila entera después, no solo el botón.

## Una etiqueta puede tener DOS trabajos (B151, 14/11/2026)

Las etiquetas del alta de cliente repiten el nombre de su sección (*"Cónyuge: cédula"* debajo del
`h5` **Cónyuge**, *"Primera referencia: teléfono"* debajo de **Referencias**). Leído como diseño es
ruido, y el arreglo parece obvio: dejar *"Cédula"*, que la sección ya dice de quién.

Es exactamente al revés, y por una razón que no se ve en la pantalla: el cartel *"Faltan datos
obligatorios"* se arma leyendo el **`textContent` de cada `<label>`**
(`clientes/ficha.jsp:1152-1166`). O sea que cada etiqueta es, además, **un renglón de una lista
fuera de contexto**. Acortada, el cartel listaría *"Nombre"* al lado de *"Nombres"* (el del
cliente) y de *"Empleador: nombre"*: tres cosas distintas, dos nombres iguales.

- **Antes de renombrar una etiqueta, grepear qué la lee**: `textContent`, `innerText`,
  `previousElementSibling`, `closest('.form-group')`, `querySelector('label')`. Si algún JS la
  cosecha, el texto no es solo de esa fila: es también el de un aviso, y ahí no hay sección arriba
  que lo desambigüe.
- **Es la familia de B103/B117 vista desde el otro lado**: no un texto fijo pegado a un dato que
  varía, sino un texto que vive en dos contextos a la vez. La prueba de si se puede acortar es
  leerlo **solo**, sin nada alrededor.
- **Si se quiere igual la etiqueta corta**, la salida no es acortarla: es darle al cartel su propio
  nombre (un `data-nombre-campo` en el grupo, que el JS prefiera al `<label>`). Eso es un ítem, no
  un cambio de redacción — y hay que pedirlo.

## El `readonly` de un campo no dice que el campo se dibuje (B150, 14/11/2026)

Diez etiquetas de la ficha del cliente llevan un `" *"` **fijo** y sus `<input>` llevan
`<%= puedeEditarFicha ? "required" : "readonly" %>` — una rama de solo lectura escrita a propósito.
De ahí sale, solo, el hallazgo *"en consulta se marca obligatorio un campo que no se puede
escribir"*. Y es falso: las tres secciones enteras (Cónyuge, Trabajo e ingresos, Referencias) están
**dentro de un `if (puedeEditarFicha)`**, así que para el único perfil con CONSULTA —la cajera— no
se dibujan (D2 de RF38, 13/10/2026). El asterisco solo lo ve quien puede editar.

- **El atributo de un campo describe el campo; quién lo ve lo decide el bloque que lo encierra.**
  Antes de afirmar "esto se ve así en el perfil X", subir en el archivo hasta el `if` que abre la
  sección — en una JSP larga puede estar 200 líneas arriba.
- Un mapa barato de esa estructura, que vale más que leer 1.800 líneas:
  `grep -n "puedeEditarFicha) {\|<% } %>\|<h5" archivo.jsp` y mirar los números.
- **Y la rama muerta no se toca** (§13): esos `readonly` no molestan a nadie. Borrarlos es un
  cambio sin hallazgo detrás.

## La columna de acciones va con `<th></th>` (medido el 14/11/2026)

**16** tablas del sistema dejan el encabezado de la columna de botones vacío; **una** lo nombra
(`usuarios/lista.jsp:39`, *"Acciones"*). Al agregar una tabla nueva, va el vacío. Y al "arreglar"
uno de los dos lados: es un ítem **transversal** (las 16 o la 1), nunca una pantalla sola —
cambiar una no unifica nada, deja dos excepciones en vez de una.

## La paginación del sistema tiene una sola forma (B148, 14/11/2026)

`pagination` aparece en **un** archivo de todo `src/main/webapp`: `clientes/_resultados.jsp`.
Clientes es el único listado paginado, así que ese bloque es el modelo para los que vengan:
« Anterior, la primera, hasta cinco números alrededor de la actual con `…` cuando hay salto, la
última, Siguiente », y *"Página X de Y"* al lado.

- **Lo navegable lleva `class="listado-vivo-pagina"`, `data-pagina` y un href real** (respaldo sin
  JavaScript); ese es el contrato con `recursos/js/listado-vivo.js` y no se toca.
- **Lo NO navegable es `<span>` sin esa clase**: el anterior en la página 1, el siguiente en la
  última, los `…` y la página actual. Un `<a>` con la clase y `data-pagina="0"` pediría una página
  que no existe, y el listener no distingue.
- **Al comparar números de página, `intValue()`**: `paginaActual` y el número de la ventana son
  `Integer`, así que `==` entre ellos compara **referencias** — anda por el caché de `Integer`
  hasta 127 y falla de la página 128 en adelante, que es justo donde nadie prueba.

## Una barra `sticky` al fondo de un formulario largo (B152, 14/11/2026)

La barra de acciones del alta de cliente (*"Registrar cliente"* / *"Volver"*) ahora se queda a la
vista con `position: sticky; bottom: 0` (`sgcpc-tema.css` §10). Lo que conviene saber antes de
copiar el patrón:

- **Funciona porque el bloque contenedor es más alto que la ventana.** La barra es el **último
  hijo** del `<fieldset>` del formulario: su posición natural está al final, muy por debajo de la
  ventana, así que `bottom: 0` la sube y la pega al borde inferior mientras ese fieldset siga
  cruzando la pantalla, y la deja en su lugar real al llegar al final. Si el contenedor midiera
  menos que la ventana, la regla no haría nada — y eso no se ve leyendo el CSS.
- **`sticky` muere en silencio si un ancestro recorta**, así que se grepean las reglas `overflow`
  del CSS vendorizado **sobre los ancestros reales**. Medido acá: las seis que tocan
  `.card`/`.card-body`/`.content-wrapper` están acotadas a clases que estas pantallas **no** usan
  (`.accordion>.card`, `.card.maximized-card`, `.card.height-control`, `.direct-chat`,
  `.content-wrapper.iframe-mode`, `.content-wrapper.kanban`). Buscar `\.card` a secas en un
  `.min.css` no alcanza: hay que partir por `}` y leer el selector completo.
- **El `z-index` se elige contra lo que puede aparecer encima.** 2 alcanza para quedar sobre los
  campos; SweetAlert2 vive en 1060, así que la barra nunca puede tapar un cartel de confirmación.
- **Y el marcado no se mueve para esto.** Bastó agregarle la clase al `<div>` que ya estaba: mover
  el `<a>` de *"Volver"* fuera del `<fieldset>` habría reabierto B109.

## Un contador en vivo y su cartel son el mismo dato (B152, 14/11/2026)

El *"Faltan N datos obligatorios."* de la barra y el cartel *"Faltan datos obligatorios"* del envío
salen de **una sola función** (`nombresDeLoQueFalta`), extraída del validador que ya existía. Dos
cuentas "equivalentes" se separan en el primer cambio (B122), y acá el usuario las ve **a la vez**.

- **El aviso de "falta que escribas algo" va en gris, no en rojo** (B95): con el campo vacío no hay
  nada mal escrito. Y no se muestra cuando no falta nada: un contador en 0 es ruido.
- **Un contador no se muestra sobre campos que todavía no se pueden escribir.** En el alta el
  `<fieldset>` arranca deshabilitado hasta que la precalificación califica; mostrar *"faltan 25"*
  ahí sería contar lo que el usuario no puede completar.
- **Si el estado lo cambia un `fetch`, escucharlo con `MutationObserver`** sobre el atributo
  (`disabled`), no llamando a este bloque desde el otro: el `fetch` resuelve **después** de
  cualquier evento del formulario, así que sin eso el contador se queda con el estado viejo hasta la
  próxima tecla — y acoplar los dos scripts los vuelve un solo bloque imposible de mover.
- **El recuento va diferido un tick** (`setTimeout(fn, 0)`): varios toggles de la misma pantalla
  sincronizan `.required` escuchando el **mismo** evento `change`, así que contar en el momento da
  el número de antes del ajuste. Si en una pantalla hay listeners que se ajustan entre sí, el que
  **lee** el resultado corre último, a propósito y con el comentario que lo explique.

## Un formulario con campos dentro de una tabla va en modal, y el modal ES la confirmación (B155, 14/11/2026)

La baja de un cliente se cargaba en una fila desplegable **dentro** de la tabla
(`data-toggle="collapse"` + `<tr class="collapse">`): el botón quedaba en una fila y el motivo
aparecía en otra, así que en una tabla rayada se perdía **de quién** era la baja que se confirmaba.

- **El patrón del sistema para esto ya existía y es uno solo**: `mora/_resultados.jsp` — **un modal
  por fila**, dibujado después de la tabla, **dentro del mismo fragmento** del listado vivo. Es
  Bootstrap puro (`data-toggle="modal"`/`data-target`, delegado en `document`), así que **sobrevive
  solo** al reemplazo del fragmento por AJAX: no hay nada que re-atar y `listado-vivo.js` no tiene
  que saber de esto. Un modal reusado y rellenado por JS obligaría a escribir la URL desde el
  navegador — más código y un dueño menos claro.
- **El modal es la confirmación: no se le agrega `data-confirmar`.** Si ya tiene título con el
  nombre del cliente, el efecto en una línea y un botón que dice la operación (*"Sí, dar de baja"*),
  un SweetAlert2 encima serían **dos diálogos para una decisión** (B123: un problema se avisa una
  vez). El modal de mora tampoco lo lleva: esa es la convención.
- **El foco-en-cancelar de B105 no se aplica por reflejo**: hay que pasar los **tres** criterios
  (¿mueve dinero?, ¿emite algo numerado?, ¿se puede volver atrás?). La baja los falla todos —es
  reversible con *"Reactivar"*—, así que abre normal.
- **Al cambiar el marcado de una fila, grepear el humo por la RUTA y los NOMBRES DE CAMPO, no por
  el marcado**: el humo postea `/clientes/{id}/baja` con `motivoBaja`/`detalleBaja` directo, así que
  mientras esos tres no cambien, el rediseño le es invisible.
- **Un `maxlength` se copia de la columna, no se elige**: el del detalle es 300 porque
  `personas_baja.detalle_baja` es `varchar(300)`, verificado en la base. Un `maxlength` inventado
  más corto que la columna recorta datos en silencio.

## Un bloque de filtros plegable: el servidor decide si abre, el navegador cuenta (B156, 14/11/2026)

Los 8 filtros del listado de clientes estaban siempre desplegados y empujaban la tabla fuera de la
primera pantalla. Quedaron a la vista los dos de todos los días y los otros 6 en un `collapse` con
un botón *"Más filtros (N)"*. Las dos mitades tienen dueños distintos **a propósito**:

- **Si el bloque abre desplegado lo decide el SERVIDOR**, contando en la JSP cuántos filtros
  plegables vienen con valor en el objeto de filtro que ya está en el request. Un filtro aplicado
  no puede quedar escondido: el usuario vería una lista recortada sin ninguna explicación a la
  vista. Nada de `localStorage` para esto — el estado correcto se deduce del pedido.
- **Un filtro con valor por defecto NO se cuenta.** *"Estado"* arranca en *"Activos"*: sumarlo
  diría *"1 filtro"* en una lista que nadie filtró.
- **El número, en cambio, lo mantiene el navegador**, y el motivo es el mecanismo del listado vivo:
  `listado-vivo.js` reemplaza **solo** el contenedor de resultados, así que la tarjeta de filtros no
  se vuelve a dibujar en una búsqueda en vivo y un número calculado en el servidor quedaría
  mostrando la cantidad de la carga anterior. El **texto** sigue siendo de la JSP y el JS reemplaza
  **solo el número** (B92). Antes de poner un contador en pantalla, preguntarse **qué parte del DOM
  se redibuja** cuando ese número cambia.
- **Recuento diferido un tick**: *"Limpiar"* vacía los campos y recién entonces dispara `change`;
  contando en el momento se lee el valor anterior. Mismo caso que el contador de B152.
- **Esconder con `collapse` no saca el campo del formulario**: un campo oculto **igual se envía**.
  Eso es lo que permite que el filtrado, los botones de informe (`form=` + `formaction`) y el
  listado vivo sigan idénticos — y es también la razón por la que el bloque tiene que abrirse solo
  cuando hay algo aplicado.
- **La flecha sigue a `aria-expanded`**, el atributo que mantiene Bootstrap, nunca una clase que el
  JS tenga que acordarse de poner; y como es movimiento, lleva su `prefers-reduced-motion`.

## Un cambio de CSS no se puede probar antes del Clean and Build (14/11/2026)

Es el gemelo del punto 11b de `sgcpc-cambio-seguro` —el que dice que una JSP que usa una clase
nueva queda en 500 hasta el Clean and Build— y costó un viaje de ida y vuelta con Joel: se le pidió
probar con **Ctrl+F5** un cambio que estaba solo en el `.css`, y reportó como defecto que la flecha
de *"Más filtros"* no giraba. **El código estaba bien**: la regla estaba en el archivo que servía
Tomcat (verificado con `curl … | grep`), FontAwesome en modo webfont le da `display:inline-block`
al `<i>` (así que `transform` aplica), y Bootstrap sí voltea `aria-expanded` en el trigger
(`removeClass('collapsed').attr("aria-expanded", true)` en su `show()`, buscando el trigger por
`data-target`). Lo que faltaba era el CSS **en el navegador**.

La causa es del propio proyecto: `cabecera.jsp` enlaza el tema como
`sgcpc-tema.css?v=<VersionDespliegue.VALOR>` y ese valor es
`String.valueOf(System.currentTimeMillis())` **capturado al cargar la clase**, o sea en el
despliegue. Mientras no se redespliegue, la URL del CSS es **idéntica** y el navegador se queda con
su copia: Ctrl+F5 *debería* refrescarla, pero no se puede contar con eso para una subpetición.

- **Entonces, un ítem que solo toca `.css` se declara "no visto" hasta el Clean and Build**, igual
  que uno que toca Java. No se le pide a Joel que lo mire antes: lo único que se consigue es que
  reporte como defecto algo que todavía no le llegó.
- **Y cuando repórte un defecto visual, el primer paso es `curl` al recurso servido + `grep` de la
  regla.** Si está, el problema no es la regla: es qué copia tiene el navegador, y se resuelve con
  el redespliegue, no "arreglando" CSS que ya funciona.
- **La prueba de 5 segundos que lo distingue**, si hace falta pedírsela: con el bloque abierto, en
  la consola, `document.querySelector('.cc-filtros-toggle').getAttribute('aria-expanded')` — si
  dice `"true"`, el mecanismo está bien y lo que falta es el CSS.

## Agregarle un ícono a un encabezado aprieta toda la tabla (B157, 14/11/2026)

Las flechitas de orden (`fa-sort`, `fa-sort-up`, `fa-sort-down`) parecen gratis y no lo son:
ensanchan cada `<th>` y el ancho sale de algún lado. En el listado de clientes, con 8 columnas, el
resultado fue que *"Estado"* se separó de su flecha y el botón *"Ver / editar"* se partió en dos
renglones — lo vio Joel en pantalla, no una medición.

- **Lo que no se parte lleva `nowrap`**: el rótulo con su flecha y la **celda de acciones completa**
  (así tampoco se parten *"Dar de baja"* ni *"Reactivar"*). Una clase en el `<td>` alcanza y queda
  acotada a esa tabla.
- **Y el ancho que sobra lo resuelve `.table-responsive`**, que esas tablas ya tienen: es mejor una
  barra horizontal que un rótulo partido. Lo que no hay que hacer es bajar la letra para que entre
  (ver el piso de B128).
- **Al agregar cualquier cosa a un `<th>`** —un ícono, un enlace, un contador— mirar la fila de
  datos más larga después, no solo el encabezado: el que se rompe suele ser el botón de la última
  columna.

## La paginación del sistema vive en `plantilla/paginacion.jsp` (B167, 14/11/2026)

Corrige lo que decía la sección de B148 ("Clientes es el único listado paginado, así que ese bloque
es el modelo para los que vengan"): cuando Solicitudes necesitó la misma paginación, Joel eligió
**extraerla** en vez de copiarla. Ahora hay **un solo** marcado de paginación y lo incluyen las dos
pantallas.

- **Cómo se usa**: la pantalla deja en el request `paginacionUrlBase` —la URL ya armada, con los
  filtros y el orden vigentes, terminada en `?` o en `&`— y el fragmento le pega `pagina=N`. Nada
  más. `pagina` y `totalPaginas` los pone el controlador, como ya lo hacían las dos.
- **Las variables del fragmento van prefijadas (`pag…`)**: se incluye con `<%@ include %>`
  (traducción estática) dentro del mismo `_jspService` que el fragmento de resultados de cada
  listado, así que un nombre repetido **no compila**.
- **Al extraer marcado compartido, el marcado no se "mejora" en el mismo paso**: se copió tal cual
  el de B148, para que la pantalla que ya estaba probada no cambie ni un píxel. Si hay algo que
  mejorar, es otro ítem.
- **Y el contador de resultados no es parte del fragmento**, a propósito: cada listado cuenta lo
  suyo y con su plural. Lo que sí hay que revisar al paginar un listado que antes no paginaba es
  que ese contador pase a usar el **total** y no `lista.size()`, que desde el corte son 20 como
  máximo (pasó acá: habría dicho "20 solicitudes encontradas" sobre 553).

## En una ventana, el foco en descartar es `autofocus` (B168, 14/11/2026)

Completa la sección de B155. El patrón de la ventana es el mismo, pero la pregunta del foco se
contesta **por acción, no por pantalla**: la baja de un cliente es reversible y abre normal; cancelar
una solicitud **no se deshace**, así que entra en el tercer criterio de **B105** y abre con el foco
en descartar.

- **En una ventana de Bootstrap no existe el `focusCancel` de SweetAlert2**: se pone `autofocus` en
  el botón de *"No, volver"*. Es la única forma de que un Enter de más no ejecute la acción.
- **El `data-confirmar-foco="cancelar"` que tenía el formulario viejo es la prueba de que el ítem
  califica**: si una acción ya venía con el foco en descartar, al mudarla a una ventana hay que
  conservar esa decisión, no empezar de cero.
- **Y la ventana va al final del cuerpo de la página**, fuera de las tarjetas: un ancestro con
  `transform`, `filter` o `perspective` convierte el `position: fixed` del modal en relativo a ese
  ancestro, y la ventana aparece recortada o corrida. `mora/_resultados.jsp` ya lo hacía así.

## Agregar una columna a una tabla con dos vistas (B174, 14/11/2026)

La bandeja de Aprobaciones dibuja dos juegos de columnas con la misma JSP (Pendientes e
Historial). Al sumarle *"Enviada por"* a una sola de las dos aparecieron tres cosas que conviene
tener juntas:

- **El `colspan` del estado vacío se actualiza en el mismo cambio.** Era
  `vistaHistorial ? 10 : 8` y Pendientes pasó a 9. Nadie lo nota hasta que la tabla queda vacía —
  y entonces el mensaje aparece corrido, en la pantalla donde menos se mira.
- **La columna nueva va donde la otra vista tiene la suya** (acá, al final, donde el Historial pone
  *"Decidida por"*): así las primeras columnas son iguales en las dos pestañas y la tabla no
  "salta" al cambiar de vista.
- **Y el dato de cada fila se resuelve con un mapa, no con una consulta por fila**: una por usuario
  distinto, como el mapa de decisores que ya existía, y **solo en la vista que lo muestra**.

## Un contador sirve en la pestaña que NO estás mirando (B172, 14/11/2026)

*"Pendientes (38)"* es útil **desde el Historial**; estando en Pendientes, el contador de
resultados de arriba de la tabla ya lo dice. Y el inverso no sirve: un *"Historial (409)"* crece
sin techo y no ayuda a decidir nada.

- **Antes de poner un número en una pestaña, preguntarse desde dónde se lo va a leer.** Si el dato
  ya está en la pantalla que esa pestaña abre, el número en la pestaña solo tiene sentido para la
  otra.
- **Y se cuenta lo que la pestaña significa, no lo que el filtro deja**: *"las que esperan"*, no
  *"las que coinciden con la búsqueda"* — si no, el número baja al escribir en el buscador y deja
  de ser el tamaño de la cola.
