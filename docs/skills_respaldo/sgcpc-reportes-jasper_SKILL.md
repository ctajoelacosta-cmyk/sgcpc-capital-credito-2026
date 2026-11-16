---
name: sgcpc-reportes-jasper
description: Usar al tocar cualquier .jrxml o lógica de generación de PDF en SGCPC (recibo, factura, contrato, pagaré, constancias, comprobantes, los 43 informes). Estilo común entre reportes, datos fiscales intocables, fuentes embebidas para portabilidad, recompilación del .jasper y vista previa sin afectar documentos ya emitidos.
---

# Reportes Jasper en SGCPC (43 .jrxml)

## Los documentos ya emitidos no cambian (D-94)

Un recibo, factura o constancia ya generado para una operación real **nunca se regenera
retroactivamente** aunque se corrija el `.jrxml` después. El cambio aplica solo a los
documentos que se generen de ahí en adelante. No existe ningún camino para "actualizar" un PDF
viejo con el diseño nuevo — si hace falta, es una decisión de Joel, no un efecto automático del
cambio.

## Estilo común a los 43 reportes (definido una sola vez, no repetido en cada .jrxml)

- **Fuentes, tamaños, colores y márgenes** iguales entre los 43 — un cambio de estilo general
  se define en un solo lugar reutilizable (plantilla/estilo compartido de JasperReports, no
  copiado y pegado en cada `.jrxml`) y se aplica a todos a la vez, nunca reporte por reporte.
- **Encabezado y pie compartidos** (`encabezado_informe.jrxml`/`encabezado_constancia.jrxml` ya
  existen con ese propósito — reusarlos, no crear un tercer estilo de encabezado).
- **Montos siempre alineados a la derecha, con separador de miles** (`1.806.750`, nunca
  `1806750`), en toda tabla de todo reporte.
- **Sin huecos en blanco** entre secciones ni páginas (ya hay casos documentados y corregidos
  de esto — B12/B59 del recorrido — no reintroducirlos en otro reporte al tocar el estilo
  común).

## Fuentes embebidas, para que el PDF se vea igual en cualquier servidor

Los 43 `.jrxml` usan `fontName="DejaVu Sans"`. **Ya está resuelto, no hace falta nada nuevo**:
la dependencia Maven `jasperreports-fonts` (ver `pom.xml`, junto a `jasperreports`) trae DejaVu
Sans empaquetada como extensión de fuentes, registrada sola con estar en el classpath — nunca
depende de una fuente instalada en el servidor de destino, y nunca Arial ni una fuente de
Windows (`docs/REGLAS_DESARROLLO.md` § 6, D-17/D-68). Si algún cambio de estilo agrega una
fuente nueva, tiene que venir con el mismo mecanismo (una dependencia de fuentes empaquetada),
nunca una fuente que solo exista en esta computadora.

## En la factura, nunca quitar ni alterar los datos fiscales

Estos campos son obligatorios y no se tocan por una mejora de diseño:
RUC, timbrado y su vigencia, numeración (establecimiento/punto de expedición/correlativo),
condición de venta, datos del cliente, las columnas **Exentas / 5 % / 10 %**, y el cuadro de
**liquidación del IVA**. Un cambio de diseño puede reordenar o reestilizar estos datos, pero
nunca puede dejar alguno afuera ni cambiar el número que muestran — eso es lógica/cálculo, no
diseño (ver `sgcpc-cambio-seguro`: lógica y cálculos no se tocan en un cambio de aspecto).

## Después de editar un `.jrxml`

1. **Vista previa primero**: generar una vista previa en un archivo aparte, para que Joel la
   vea — nunca sobrescribir el PDF ya guardado de una operación real, y nunca generarla contra
   `sgcpc_db`. Ver el ciclo de abajo.
2. **Recién con el OK de Joel sobre la vista previa**, recompilar el `.jasper`
   correspondiente: `herramientas/compilar_reportes.sh <nombre_del_reporte>` (sin argumento
   compila los 43 — evitarlo si se editó uno solo, para no generar ruido de bytes distintos en
   los demás `.jasper` sin cambio real de diseño). Confirmar con `git status --short` que quedó
   **un solo `.jasper` modificado** y **una sola línea** del manifiesto
   `herramientas/reportes_jasper.sha256` (más su fecha de cabecera).
3. **Commitear el `.jrxml` y su `.jasper` juntos**, en el mismo commit — nunca uno sin el otro.
   `ReportesCompiladosTest` (corre con cualquier `mvn test`) falla si quedan desincronizados;
   confirmar que pasa antes de comitear. **Entre la edición y la recompilación `mvn test` falla
   a propósito**, nombrando ese `.jrxml`: avisarle a Joel que esa falla es la esperada, y que
   **nadie debe emitir ese documento mientras tanto** (se serviría el `.jasper` viejo).

**El manifiesto depende de los BYTES del árbol de trabajo, no del contenido versionado**
(medido el 16/11/2026, armando el `.war` de la entrega del libro). `ReportesCompiladosTest`
compara el SHA-256 del `.jrxml` **tal cual está en disco**, y el repositorio está con
`core.autocrlf=true`: en un **checkout nuevo** (un `git worktree`, otra PC, un clon) los `.jrxml`
salen con **CRLF** y la prueba falla nombrando hasta 33 reportes que en realidad están bien — los
`.jasper` son byte a byte idénticos. Pasarlos todos a LF **tampoco alcanza**: el manifiesto se
generó sobre un árbol con finales de línea **mixtos**, así que después fallan los otros 10. Para
compilar fuera del árbol de Joel, **copiar los 43 `.jrxml` del árbol de trabajo**, byte a byte.

## Un documento que no se guarda se RECONSTRUYE al imprimir (B102, 30/10/2026)

Antes de prometerle a Joel qué texto va a decir un documento, hay que leer **el camino de
impresión**, no el de emisión. La factura **no guarda sus renglones**: se reconstruyen de
`aplicaciones_pago` cada vez que se imprime (D-93/D-94), así que lo que se ve en el papel sale de
`ServicioCobranza.derivarLineasCuotaFactura` (*"Interés compensatorio cuota 1/6"*) y **no** de
`construirLineasCuota`, que corre al emitir y solo sirve para **sumar los totales** (*"Intereses
cuota 1 — CC-2026-00004 — vto. 30/10/2026"*). Son dos redacciones del mismo concepto y ya dicen
cosas distintas — el defecto quedó como B102; la lección es la de leer el camino correcto. Lo mismo
vale para el **comprobante de desembolso**, que tampoco se guarda: cualquier dato que lea del plan
(el primer vencimiento, por ejemplo) sale **del plan de hoy**, no del que existía cuando se firmó.

## Ciclo de verificación visual: PDF → PNG, y lo miro yo antes de pasárselo a Joel

Modelo ya probado: `GenerarMuestrasFacturaTemporal` (27/10/2026) y
`GenerarMuestrasDevolucionTemporal` (28/10/2026), las dos en
`src/test/.../herramientas/`, temporales y anotadas en la lista "Antes de entregar" de
`AVANCE.md`.

- **Compilar el `.jrxml` EN MEMORIA** con `JasperCompileManager.compileReport(ruta)`: el
  `.jasper` commiteado queda intacto hasta que Joel apruebe el diseño.
- **Exportar PDF y PNG**. El PNG lo saca el propio JasperReports con `JRGraphics2DExporter`
  sobre un `BufferedImage` (`ZOOM_PNG = 2.0f`) — **no hace falta instalar nada** (ni
  `pdftoppm`, ni ImageMagick), lo que mantiene el "todo offline".
- **Mirar el PNG antes de mostrárselo a Joel.** No es opcional: en B78 el primer intento dejaba
  **16 pt** sobre la línea de la firma nueva (contra los 60 pt de las de arriba) — imposible de
  firmar; se vio en el PNG y se corrigió antes de que Joel perdiera un viaje.
- **Un escenario por rama del documento**: si un texto cambia según los datos (ej.
  `TEXTO_CIERRE_OPERACION`, con y sin cancelación total), generar las dos.
- **Datos de ejemplo marcados `(MUESTRA)`**, y los valores formateados **igual que el sistema
  real** (`FormateadorNumeros.comoGuaranies` deja `8.500.000 Gs.`, no `8.500.000`) — una muestra
  que miente sobre el formato no sirve para aprobar un diseño. El texto que arma Java se pide a
  **la misma clase que usa el sistema**, nunca se copia a mano en la muestra.
- **El PDF va a una carpeta fuera del repo** para que Joel lo abra
  (`../pruebas/<reporte>_muestra/`, por `-DsalidaPdf`), y el PNG a `target/`.
- **DOGMA 1: la vista previa NO es "probado".** Muestra el diseño, no el circuito. Después de
  recompilar y del Clean and Build, el documento tiene que emitirse de verdad desde la pantalla.
  Ojo con los documentos de numeración única e idempotente (una constancia que no se regenera):
  **si se emite con el `.jasper` viejo, se quema el número** — recompilar primero.

## Un texto que describe un cálculo es parte del cálculo (C3/C12, 29/10/2026)

Varias frases de los documentos **describen** lo que hace el motor financiero. Si el cálculo y la
frase no dicen lo mismo, el documento firmado miente, y eso no se nota en ninguna prueba de diseño:

- **La frase se prueba con JUnit contra el cálculo real**, no leyendo la API ni el javadoc. La
  cláusula SEGUNDA decía *"las siguientes el mismo día de cada mes"* y el plan anclaba al día
  **elegido** (antes del corrimiento a día hábil): dos años conviviendo sin que nadie lo viera. El
  caso de borde (día 29, 30 o 31 → `plusMonths` resuelve al último día del mes) también se probó
  sobre un plan generado de verdad.
- **El texto se arregla en el texto**, no cambiando el cálculo, salvo que Joel decida lo contrario:
  tocar `generarPlanPagos` para que coincida con una frase es cambiar plata por una redacción.
- **Un dato con dos formas (elegida/efectiva, con IVA/sin IVA) necesita un dueño único.** La fecha
  efectiva de la cuota 1 vivía en línea en la formalización y el comprobante de desembolso usaba la
  otra: el mismo documento que el cliente firma decía una fecha distinta del contrato. Hoy sale de
  `ServicioFormalizacion.primeraFechaVencimientoEfectiva`.
- **Antes de tocar una frase, buscarla en los 43 `.jrxml` y en el Java que arma textos** — puede
  estar repetida en otro documento. (En C3 estaba en uno solo: el pagaré no describe el anclaje.)
- **Lo que se muestra es lo que el cliente va a vivir, nunca el dato interno del cálculo.** Pasó
  tres veces en un día (C3, C12, C39): el ancla teórica de un plan, la fecha elegida al
  formalizar… todas son datos de adentro del motor. El documento muestra **la fecha en que se
  cobra**. Si un dato aparece en **varias** pantallas y además en un PDF (C39: recibo + ficha +
  vista previa), el dueño es **un método estático puro** que los tres llaman — las JSP también
  pueden invocarlo, así no se vuelve a desincronizar.
- **Ojo con los documentos que NO se guardan**: el comprobante de desembolso se **regenera** en
  cada impresión (solo la impresión queda en auditoría), a diferencia de recibos, facturas y
  constancias, que sí viven en `archivos_binarios` y nunca cambian (D-94). Corregir un dato suyo
  cambia lo que muestra la **reimpresión de operaciones viejas**: decirlo antes, es decisión de
  Joel.

## Geometría de JasperReports: tres trampas que ya costaron (B83, 29/10/2026)

- **Un `<rectangle>` (o cualquier elemento gráfico) es OPACO con fondo blanco por defecto**, y
  tapa lo que haya en el `<background>` — el sello diagonal de ANULADO/REVERTIDO, por ejemplo. Si
  el recuadro es solo un marco, va con `mode="Transparent"`. Los textos no tienen el problema: un
  `textField`/`staticText` es transparente por defecto.
- **Una banda condicional que empuja el flujo (un `pageHeader` con `printWhenExpression`) es
  incompatible con una grilla dibujada en el `<background>` a coordenadas fijas.** El background
  no se mueve: en el caso que sí imprime la banda, la grilla queda flotando sobre el texto. O la
  franja vive dentro del `title` con alto fijo (lo que hizo la factura), o la grilla se arma con
  los `box` de cada celda, que siguen el flujo (lo que hizo el recibo, porque la franja de
  reversión no se podía mover).
- **Un texto más largo no se recorta: desaparece o se corta en seco.** Al cambiar
  `"ORIGINAL – Empresa"` por `"ORIGINAL — ARCHIVO CAPITAL CRÉDITO"` el campo de 101 px mostraba
  *"ORIGINAL — ARCHIVO"* a secas. Cada vez que un texto crece, revisar el ancho (y el alto: con
  `height` chico, JasperReports **descarta** el texto entero, no lo recorta).

**Y el método que evita las tres**: diseñar sobre una **copia del `.jrxml` fuera del repositorio**
y compilar esa copia en memoria para las muestras. El `.jrxml` y el `.jasper` del repositorio
quedan sincronizados todo el tiempo que Joel revisa — `mvn test` nunca se pone en rojo y nadie
puede emitir el documento con el `.jasper` viejo. Recién con el OK se copia, se recompila **solo
ese reporte** y se confirma que el manifiesto cambió **una sola línea** por reporte.

## La muestra tiene que recibir lo MISMO que recibe el reporte en producción

01/11/2026, tanda B11/B13/C37/B102. Las 7 muestras encontraron un defecto real del sistema
(**B103**, *"a los primer día"* al formalizar un día 1 — que solo se ve en la rama del día 1, por
eso la muestra la incluía) y **dos defectos que no existían**, los dos inventados por la muestra:

- el detalle del recibo salía `null null null...` porque la muestra pasaba `JREmptyDataSource`
  donde el sistema manda `JRBeanCollectionDataSource` con una fila por cuota aplicada;
- la firma decía *"Firma de la cajera (Sofía Martínez"* **sin cerrar el paréntesis** — el `)` está
  en el `.jrxml`, pero el recuadro tiene 180 pt y la muestra le había puesto "(MUESTRA)" **al
  nombre de la cajera**, que no es donde va esa marca.

Regla: el `(MUESTRA)` va en los **identificadores** (número de documento, operación, nombre del
cliente), nunca en un campo cuyo **ancho** es parte de lo que se está revisando; y **todo
datasource se arma con el mismo DTO y los mismos valores que usa el servicio** (buscar el
`new Fila...` real en el código y copiar la forma, no inventar una). Si no, se revisa dos veces y
se le hace perder un viaje a Joel por algo que en pantalla no pasa.

## Antes de tocar un documento "se guarda al primer pedido", mirar cuántos ya existen

03/11/2026 (**C22**). El acta de arqueo no se regenera: `obtenerOGenerarPdf` la crea la primera
vez que alguien la pide y la guarda en `sesiones_caja.id_archivo_cierre` (D-94, igual que recibo,
factura y constancias; el comprobante de desembolso es el que **sí** se regenera).

- **La pregunta "¿esto cambia documentos ya emitidos?" se contesta con un `SELECT`, no de
  memoria**: `SELECT count(*) ... WHERE id_archivo_cierre IS NOT NULL` devolvió **0** sobre las
  14 sesiones, así que el cambio no pisaba nada y la pregunta 4 del control de riesgo quedó
  cerrada con evidencia.
- **Y hay que decirle a Joel que NO imprima nada antes del cambio**: la primera impresión
  congela el formato de esa sesión para siempre. En C22 eso además permitió probar las **dos**
  ramas con sesiones viejas (una sin revisor y otra con diferencia ya revisada), sin abrir ni
  cerrar una caja.
- **Cuando la plantilla concatena un prefijo fijo a un dato que puede faltar, el prefijo miente**
  (misma lección que B103, ahora en un `.jrxml`): `"Recibí: " + $P{GERENTE_RECIBIO}` no podía
  saber si había alguien que hubiera recibido algo. La línea entera pasó a decidirla Java
  (`ServicioCaja.lineaRecibiDelArqueo`, pura) y el textField imprime `$P{RECIBI_LINEA}` tal cual.
- **Un comentario XML no puede contener `--`**: `<!-- ... -- texto -->` rompe la compilación del
  `.jrxml` con `SAXParseException: La cadena "--" no está permitida en los comentarios`. En los
  comentarios de un `.jrxml` va coma o punto, nunca el guion doble que se usa en los javadoc de
  este proyecto. **Esto vale para TODO XML del proyecto** (`pom.xml`, `web.xml`, el
  `AndroidManifest.xml` de la app móvil): ver la sección del 12/11/2026 en
  `sgcpc-cambio-seguro`, con el control para pasar después de editar cualquiera.

## Un defecto "del PDF" puede estar en Java, y estar escrito dos veces (B52 + B53, 07/11/2026)

Los dos ítems del arqueo venían anotados como si fueran de la plantilla (`arqueo_caja.jrxml`), y
**uno de los dos no la tocaba**:

- **Antes de abrir el `.jrxml`, buscar de dónde sale el valor.** El *"−0 Gs."* nacía en
  `ServicioCaja` —`(signo < 0 ? "-" : "") + monto`, sin mirar el monto—, así que se arregló en Java
  y **sin recompilar nada**. La plantilla solo imprimía el campo. Es el mismo criterio que la
  sección de abajo: un texto que parece de la plantilla puede venir armado de Java.
- **Y el mismo armador puede estar en la pantalla además del PDF.** Esa expresión existía **dos
  veces**: la tabla de movimientos de la ficha y el detalle del arqueo. Arreglar solo el PDF
  dejaba la pantalla con el defecto. Antes de dar por cerrado un defecto de formato de un dato,
  grepear la expresión completa: si aparece dos veces, el arreglo es **un armador** que las
  reemplace, no dos parches.
- **Lo que sí exige el `.jrxml` es el ANCHO, y se mide contra el dato más largo real.** Pasar de
  `hh:mm` a `dd/mm/aaaa hh:mm` no entra en 50 px: la columna fue a 80 y las demás se corrieron
  **conservando el borde derecho** y el `x`/ancho de la columna de monto (alineada a la derecha, es
  la que no se puede mover sin que se note). Antes de reacomodar, confirmar si esa tabla tiene
  **encabezados de columna**: si los tiene, hay que moverlos también.
- **Verificar la muestra sin congelar el documento.** El arqueo se guarda al primer pedido, así que
  la vista previa llama al **generador** (`generarPdfArqueo`, privado — se alcanza por reflexión
  desde la herramienta temporal) y **nunca** a `obtenerPdfArqueo`, que es el que escribe
  `id_archivo_cierre`. Se comprueba después con un `SELECT` que la sesión siguió en `NULL`.
- **Y ojo con la comprobación automática sobre el texto extraído del PDF**: OpenPDF concatena las
  columnas **sin separador**, así que un `contains("-0 Gs.")` dio un **falso positivo** —las
  columnas "operación" y "comprobante" valen `-` cuando el movimiento no tiene referencia, y
  `- ` + `- ` + `0 Gs.` se lee `--0 Gs.`—. Lo que se extrae del PDF sirve para ver si un texto
  **está completo o cortado** (eso sí es inequívoco); para verificar el **valor** de una columna,
  pedírselo al mismo método que usa el servicio, no parsear el texto.

## Las BANDAS no admiten `printWhenExpression`: un encabezado condicional deja un hueco (B12, 07/11/2026)

Solo los **elementos** tienen `printWhenExpression`; la **banda** reserva su altura declarada
igual. Por eso un `pageHeader` que se oculta en la página 1 deja ahí un hueco del tamaño de su
altura, y `positionType="Float"` **no** exime de eso (gobierna el desplazamiento en tiempo de
ejecución, no la geometría declarada). Afecta a los tres reportes de varias páginas del sistema
(`contrato`, `plan_pagos`, `pagare`), los tres con 80 pt.

- **Las dos salidas, y conviene medir las dos antes de elegir**: el **subreporte común** de
  encabezado lo elimina (hueco de 83 → 3 pt) pero toca los tres reportes y uno nuevo; el
  **membrete de continuación** —reducir el repetido a una línea— lo achica (83 → 31) tocando un
  solo archivo. La diferencia se mide **renderizando las dos**, sobre copias fuera del repositorio
  compiladas en memoria, y se le lleva a Joel con los números: la elección es suya.
- **El membrete de continuación no pierde información si la página 1 ya la trae.** La banda
  `title` lleva el membrete completo (RUC, domicilio, teléfono, registro), así que en las páginas
  siguientes alcanza con logo, razón social y el número del documento. Antes de recortarlo,
  confirmar que lo que se saca **está en la página 1**.
- **Al armar la línea nueva, revisar que los campos no se pisen** (`x + width` de uno contra el
  `x` del siguiente): el primer borrador de B12 tenía la razón social llegando a 346 y el número
  arrancando en 331.
- **Y el `(MUESTRA)` no va en el campo cuyo ancho se está revisando** — regla ya escrita, que acá
  volvió a aparecer con otra cara: el identificador lleva la marca en las muestras, pero es
  justamente lo que se imprime en el membrete nuevo, así que con la marca la vista previa mostraba
  cortado un texto que en el papel real entra. En esa muestra el identificador va con su **formato
  real** (acá `CC-AAAA-NNNNN`, 13 caracteres fijos) y la marca se queda en el nombre del cliente.

## Un ancho se verifica RENDERIZANDO, y el "dato más largo" tiene dos versiones (B108, 07/11/2026)

El recuadro de firma del recibo medía 180 pt y cortaba el nombre. Lo que dejó escrito:

- **No multiplicar caracteres por puntos.** La frase que ya se cortaba tenía **35** caracteres en
  180 pt, o sea ~5,3 pt por carácter — bastante más de lo que da cualquier estimación "a ojo" para
  DejaVu Sans 8. La única medición que vale es **generar la muestra con el texto más largo y
  mirar**. Un ancho "calculado" es una suposición disfrazada de número.
- **"El dato más largo real" son DOS preguntas**: el más largo que **ya existe** (acá, los 171
  recibos emitidos, todos de la misma cajera, 35 caracteres) y el más largo que **puede existir**
  (38, porque el ADMINISTRADOR tiene nivel TOTAL en todos los permisos y puede cobrar y firmar).
  Se dimensiona contra el segundo, y se dicen los dos: el primero explica por qué el defecto se vio
  hoy, el segundo por qué el ancho nuevo alcanza mañana.
- **Al ensanchar un campo centrado, mirar sobre qué está centrado.** Acá el campo estaba centrado
  sobre la línea de firma; se ensanchó **desde x=0** para que el centro no se moviera. Si el centro
  se corre, el texto queda desalineado respecto de la línea y eso **sí** se ve. Ensanchar "hacia la
  derecha porque hay lugar" es el error fácil.
- **Y revisar que el campo ensanchado no pise al de al lado**: hay que mirar `x + width` del campo
  y el `x` del siguiente. Es el mismo chequeo que pide reacomodar una tabla (B53).

## Una muestra que falla puede estar mintiendo: comparar sus parámetros con los del servicio

B108 (07/11/2026). La primera muestra salió con **"Son: null."**. No era el recibo: la plantilla
imprime `RECIBIDO_LETRAS` y la herramienta —copiada de la de B83— solo seteaba `APLICADO_LETRAS`.
El servicio real setea **los dos**.

- Ante un `null` o un texto raro en una muestra, **el primer sospechoso es la muestra**, no el
  reporte. Se confirma buscando el parámetro en el `textFieldExpression` y comparándolo con lo que
  pone el servicio (`grep 'put("NOMBRE_DEL_PARAMETRO"'`).
- **Heredar la base de parámetros de otra herramienta temporal hereda también sus huecos.** La de
  B83 arrastra este mismo (su muestra también decía "Son: null").
- **Y por eso el cruce no se hace a ojo: va en la herramienta** (B120, 07/11/2026). En una sola
  tanda, heredar la base de la muestra del contrato para el plan de pagos produjo **cuatro**
  defectos que no existían en el papel real: *"Son: null"*, la fila Total en `null`, *"Fecha de
  formalización: null"* y un registro del BCP inventado que se leía duplicado. Cada uno costó una
  ronda. El chequeo es de diez líneas y corre antes de llenar:

  ```java
  Matcher usados = Pattern.compile("[$]P[{]([A-Z_0-9]+)[}]").matcher(fuenteDelJrxml);
  while (usados.find()) { if (!parametros.containsKey(usados.group(1))) faltantes.add(...); }
  ```

  Avisa, no falla (hay parámetros opcionales). Y los valores que **sí** importan para lo que se
  está revisando —un domicilio cuyo ancho se mide, por ejemplo— se toman de
  `parametros_sistema`, no del valor de ejemplo heredado: en B120 el domicilio de la base
  **coincidía** con el real (64 caracteres), y recién verificándolo se supo que la medición valía.

## Un parámetro declarado en el `.jrxml` no siempre se imprime desde ahí

Antes de dar por hecho que tocar un texto obliga a recompilar el `.jasper`: **buscar el
parámetro en los `textFieldExpression`**, no solo en la lista de `<parameter>`. En el contrato,
`FECHA_FORMALIZACION_LETRAS` está **declarado** pero ningún textField lo usa — el texto viaja como
fila del datasource de cláusulas, armado en Java (`construirClausulasContrato`). Por eso B103 se
resolvió **sin tocar el `.jrxml` ni el `.jasper`** y sin mover el manifiesto. Al revés también
vale: un texto que parece de Java puede estar partido entre Java y la plantilla, y ahí está el
defecto (B103 era exactamente eso: el `"a los "` en Java y el día en el conversor).

## Todo offline

Ningún reporte ni su vista previa dependen de internet (ninguna fuente externa, ninguna fuente
de Google Fonts en vivo, nada). El sistema entero funciona sin conexión (confirmado en
`docs/auditoria/AUDITORIA_TECNICA.md`, Área 6) y eso no cambia por una mejora de estilo. Si una
skill de diseño externa (UI UX Pro Max, Impeccable) sugiere algo que requiera un recurso de
internet, no se aplica.

## Un texto del documento puede no estar en ningún `.jrxml` (B127, 11/11/2026)

Segundo caso después de B52/B53, y el más amplio: el monto en letras de **siete** puntos de
documentos —recibo (aplicado y recibido), factura, constancia de cancelación, constancia de
reversión, comprobante de desembolso, contrato y pagaré— sale de **un solo método de Java**
(`ConversorNumerosALetras.comoLetrasConGuaranies`), que las plantillas reciben como
parámetro. Corregir la apócope **no tocó ningún `.jrxml` ni `.jasper`**, ni el manifiesto.

- **Antes de abrir una plantilla por un texto, grepear el parámetro en `src/main/java`**: si
  el `.jrxml` solo imprime `$P{X}`, el dueño es Java y el cambio no exige recompilar nada.
- **Y la dirección en la que va el sustantivo decide la gramática**: "Son: … GUARANÍES"
  (número delante) pide apócope; "Son guaraníes: …" (sustantivo delante) no. Se verifica
  mirando el `textFieldExpression` de **cada** plantilla que use el parámetro, no una.
- **Una muestra de un texto calculado se le pide al mismo método que usa el sistema.** La
  base de parámetros heredada de otra herramienta traía `TOTAL_A_PAGAR_LETRAS` escrito a
  mano: con eso, la muestra habría salido igual antes y después del arreglo. Lo mismo con el
  monto: el plan se genera con `ServicioMotorFinanciero` y el total se suma como lo suma
  `ServicioFormalizacion`, así el número de la muestra es el mismo que el de la pantalla
  (919.271 Gs., el de SOL-2026-0197).
- **Los valores de ejemplo heredados siguen mintiendo en lo que no se revisa**: el membrete
  de esta muestra dice *"Registro BCP: Registro BCP N.º 1234"* (duplicado) porque el valor
  viene de la herramienta de C3/C12; en la base es `Res. SB N.º 0147/2026`. Si un campo
  heredado se ve raro, **confirmarlo en `parametros_sistema` antes de anotarlo como
  defecto** — y decirlo en el informe para que Joel no lo lea como uno.
