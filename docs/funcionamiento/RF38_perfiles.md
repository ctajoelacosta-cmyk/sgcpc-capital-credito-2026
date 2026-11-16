# RF38 — Cada usuario ve solo las opciones de su perfil (RNF11)

**Estado: probado en pantalla el 13/10/2026 (tag `rf38-probado`, estado completo D1 a D5).**
Menú por perfil (D1), ficha de cliente sin secciones sensibles para quien solo consulta (D2),
navegación propia de "Cancelación anticipada" (D3), ancla que baja sola en "Devolución del
pagaré" (D4) y sin asteriscos en consulta (D5) — todo confirmado en pantalla por Joel, después
de su Clean and Build — ver §9.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF38 (módulo Seguridad): *"El sistema debe mostrar a cada usuario
solo las opciones y los informes que corresponden a su perfil."*

`docs/RF_ANTEPROYECTO.md`, RNF11 (módulo Seguridad): *"El sistema debe controlar los permisos de
cada perfil en el servidor, aunque el usuario escriba la dirección de una pantalla a mano, y
rechazar formularios enviados desde fuera del sistema."*

RF38 es el requisito de **pantalla** (qué se ve); RNF11 es el requisito de **servidor** (qué se
permite de verdad). Este documento analiza los dos juntos porque están implementados con el
mismo mecanismo (`FiltroAutorizacion`/`ServicioAutorizacion`) y una diferencia entre ambos es
justamente el tipo de hallazgo que hay que marcar.

## 2. Cómo funciona

### 2.1. Mecanismo

Tres piezas, todas en el servidor (RNF11), con el menú como capa puramente visual:

- **`ServicioAutorizacion.construirMatriz()`**: la matriz real, en memoria — un
  `Map<PerfilUsuario, Map<Permiso, NivelAcceso>>` (`NivelAcceso` = TOTAL/CONSULTA/NINGUNO), 18
  valores de `Permiso` por perfil. Pura, sin tocar sesión ni base.
- **`FiltroAutorizacion`**: un `Filter` de servlet que corre en cada request (después de
  `FiltroAutenticacion`, que exige sesión). `RUTAS_PROTEGIDAS` es un mapa ruta→`Permiso` (20
  entradas, coincidencia por prefijo, la más específica primero); `permisoDeDocumento` resuelve
  aparte las rutas con un id en medio (`/cobranza/{id}/recibo`, etc.). Regla fija: **GET/HEAD
  exige nivel != NINGUNO (CONSULTA alcanza); cualquier otro método (POST) exige TOTAL** — así
  RNF11 también cubre "formularios enviados desde fuera" (un POST directo sin pasar por la
  pantalla cae en la misma regla). Sin permiso suficiente: `403` + auditoría `ACCESO_DENEGADO`.
- **`menu.jsp`**: arma las 8 secciones del menú con booleanos `esGerente`/`esOficial`/
  `esCajero`/`esAdministrador`, **duplicados a mano** de la misma condición que ya tiene cada
  ruta en `FiltroAutorizacion`/`ServicioAutorizacion` (comentario propio del archivo: *"el
  bloqueo real lo sigue haciendo FiltroAutorizacion en el servidor; esto solo arma el HTML"*). Si
  el usuario no tiene el permiso, la opción se muestra igual pero como `<span>` con candado, no
  como `<a>` — nunca se omite del todo.

### 2.2. Menú lateral, por perfil

**Tabla original del análisis (antes de D1, §7)**: "✓" = enlace activo, "🔒" = visible con
candado (span, sin href). **Desde D1 (13/10/2026, implementado)**: "🔒" pasa a "no se dibuja
nada" — ni la opción ni, si queda sin ninguna opción visible, el módulo entero. Se deja la tabla
tal cual para no perder el detalle de qué condición gobierna cada fila; donde dice 🔒, leer
"ausente del menú". Administrador ve todo con ✓ (D7: TOTAL en los 18 permisos, sin excepción).

| Módulo | Opción | Gerente | Oficial | Cajera |
|---|---|---|---|---|
| — | Panel | ✓ | ✓ | ✓ |
| Clientes y solicitudes | Cotizador | ✓ | ✓ | ✓ |
| Clientes y solicitudes | Clientes | ✓ | ✓ | ✓ (solo consulta, ver §6b) |
| Clientes y solicitudes | Solicitudes | 🔒 | ✓ | 🔒 |
| Aprobación | Aprobaciones | ✓ | 🔒 | 🔒 |
| Formalización | Formalización | ✓ | ✓ | 🔒 |
| Desembolso y caja | Caja | ✓ (solo consulta) | 🔒 | ✓ |
| Desembolso y caja | Desembolsos | 🔒 | 🔒 | ✓ |
| Cobranza y mora | Cobranza | 🔒 | 🔒 | ✓ |
| Cobranza y mora | Seguimiento de mora | ✓ | ✓ | 🔒 |
| Cobranza y mora | Reversiones | ✓ | 🔒 | 🔒 (pero sí puede *solicitar* una, ver §6e) |
| Cancelación | Cancelación anticipada | 🔒 | 🔒 | ✓ |
| Cancelación | Devolución del pagaré | ✓ | ✓ | 🔒 |
| Informes | Préstamos | ✓ | ✓ (consulta) | 🔒 |
| Informes | Reportes | ✓ (21/21) | ✓ (5/21, ver §6d) | 🔒 |
| Seguridad | Usuarios | 🔒 | 🔒 | 🔒 |
| Seguridad | Parámetros | ✓ (solo consulta) | 🔒 | 🔒 |

### 2.3. Informes de Reportes, por perfil

`/reportes` tiene 21 sub-informes (`case` del switch de `ControladorReportesServlet`). El
permiso de ruta (`VER_REPORTES`) solo distingue "algo vs. nada": gerente TOTAL, oficial CONSULTA,
cajera NINGUNO. La restricción fina de CUÁLES de los 21 ve el oficial la hace el propio
controlador, con una lista aparte (`SUBREPORTES_OFICIAL`, 5 nombres: `vencimientos`, `morosidad`,
`gestiones`, `estado-cuenta`, `consultas-buro`) — **no está en `FiltroAutorizacion` ni se ve en
el menú** (el menú tiene un solo enlace "Reportes", no 21). Gerente/administrador ven los 21;
oficial, 5 de 21 (403 si fuerza la URL de cualquiera de los otros 16); cajera, ninguno (403 ya en
`/reportes` general, por `VER_REPORTES = NINGUNO`).

### 2.4. Matriz real de permisos en el servidor

T = TOTAL, C = CONSULTA, — = NINGUNO. Administrador = T en los 18, siempre (fila omitida).

| Permiso | Ruta(s) protegida(s) | Dónde se controla | Gerente | Oficial | Cajera |
|---|---|---|---|---|---|
| `COTIZAR` | `/cotizador` | `FiltroAutorizacion` | T | T | T |
| `REGISTRAR_EDITAR_CLIENTE` | `/clientes` | `FiltroAutorizacion` | T | T | C |
| `CARGAR_DOCUMENTOS_CONSULTA_BURO` | `/documentos` | `FiltroAutorizacion` | T | T | — |
| `GESTIONAR_SOLICITUD` | `/solicitudes` | `FiltroAutorizacion` | — | T | — |
| `APROBAR_RECHAZAR_SOLICITUD` | `/aprobaciones` | `FiltroAutorizacion` | T | — | — |
| `GENERAR_DOCUMENTOS_FORMALIZACION` | `/formalizacion` | `FiltroAutorizacion` | T | T | — |
| `ANULAR_PRESTAMO_PRE_DESEMBOLSO` | (acción dentro de `/formalizacion`) | `ServicioFormalizacion` (no es una ruta propia) | T | — | — |
| `ABRIR_CERRAR_CAJA` | `/caja` | `FiltroAutorizacion` | C | — | T |
| `ENTREGAR_RECIBIR_EFECTIVO_CAJA` | `/caja/entregar` | `FiltroAutorizacion` **+** `ServicioCaja.registrarEntregaGerente` (2.ᵃ capa, ver §6c) | T | — | T (bloqueada igual en la 2.ᵃ capa) |
| `DESEMBOLSAR` | `/desembolso` | `FiltroAutorizacion` | — | — | T |
| `COBRAR` | `/cobranza` | `FiltroAutorizacion` | — | — | T |
| `VER_COMPROBANTES_COBRANZA` | `/cobranza/{id}/recibo\|factura\|constancia\|nota-anulacion\|reversion/{id}`, `/desembolso/{id}/comprobante` | `FiltroAutorizacion.permisoDeDocumento` | C | — | C |
| `SOLICITAR_REVERSION` | `/reversiones/solicitar` | `FiltroAutorizacion` | — | — | T |
| `AUTORIZAR_REVERSION_O_DIFERENCIA` | `/caja/revisar-diferencia`, `/reversiones` (bandeja/autorizar/rechazar) | `FiltroAutorizacion` | T | — | — |
| `REGISTRAR_GESTION_COBRANZA` | `/mora` | `FiltroAutorizacion` | T | T | — (pero sí genera una gestión automática al cobrar, ver §6a) |
| `VER_PANEL_REPORTES` | *(ninguna — no está en `RUTAS_PROTEGIDAS`)* | **no se controla en ningún lado**, ver §6f | T* | C* | C* |
| `VER_REPORTES` | `/reportes`, `/prestamos` | `FiltroAutorizacion` **+** whitelist propia del controlador para oficial (§2.3) | T | C | — |
| `ADMINISTRAR_USUARIOS` | `/usuarios` | `FiltroAutorizacion` | — (D-151) | — | — |
| `ADMINISTRAR_PARAMETROS` | `/parametros` | `FiltroAutorizacion` | C | — | — |
| `CONSULTAR_AUDITORIA` | *(ninguna — `/reportes/auditoria` cae bajo `VER_REPORTES`)* | **no se controla en ningún lado**, ver §6f | T* | —* | —* |

`*` = valor definido en la matriz pero sin ningún punto del código que lo lea (§6f).

## 3. Auditoría

Cada `403` de `FiltroAutorizacion` se audita como `ACCESO_DENEGADO` / `USUARIO` (con IP, sin
navegador — el método usado ahí sí incluye `direccionIp`, a diferencia de lo señalado en PA-28
para `EXPORTAR_CSV`/`EMITIR_INFORME_PDF`). No hay auditoría de "menú mostrado" (no hace falta:
el menú no decide nada, solo refleja lo que el servidor ya decidió).

## 4. Dónde está

- **Matriz**: `enumeraciones/Permiso.java` (18 valores), `enumeraciones/NivelAcceso.java`,
  `servicios/ServicioAutorizacion.java` (`construirMatriz`).
- **Control de ruta**: `filtros/FiltroAutorizacion.java` (`RUTAS_PROTEGIDAS`, `permisoDe`,
  `permisoDeDocumento`, `autorizaAcceso`).
- **Menú**: `WEB-INF/vistas/plantilla/menu.jsp`.
- **Restricción fina de Reportes**: `controladores/ControladorReportesServlet.java`
  (`SUBREPORTES_OFICIAL`).
- **Restricción fina de Caja**: `servicios/ServicioCaja.registrarEntregaGerente` (valida
  `perfilUsuario == GERENTE_ADMINISTRADOR`, además del permiso de ruta).
- **Ficha de cliente filtrada por perfil (D2/D5, 13/10/2026)**: `WEB-INF/vistas/clientes/ficha.jsp`
  (`puedeEditarFicha`, de `ControladorClientesServlet.puedeEditar`).
- **Navegación del menú a una parte de otra pantalla (D3/D4, 13/10/2026)**:
  `WEB-INF/vistas/plantilla/menu.jsp` (rutas `/cobranza?origen=cancelacion-anticipada` y
  `/formalizacion#cierre-documental`), `WEB-INF/vistas/cobranza/busqueda.jsp` (aviso/título de
  "Cancelación anticipada"), `WEB-INF/vistas/plantilla/pie.jsp` (resaltado del menú para ambas
  rutas + `scrollIntoView` en `window.load` para el ancla).

## 5. Dependencias

- **RF37** (usuarios/perfiles): este RF asigna el `PerfilUsuario` que RF38 después usa para
  decidir qué se ve y qué se permite — sin RF37 no hay perfil que consultar.
- **RNF11**: mismo mecanismo, analizado junto (§1).
- **Todos los demás RF de pantalla**: cada uno declara su propia condición de perfil en
  `menu.jsp`/`FiltroAutorizacion`; este documento es el único que mira el conjunto completo.

## 6. Análisis

Comparado contra `docs/00_especificacion_maestra.md` §3.1 (la matriz de permisos oficial, con
las notas D7/D-151 ya incorporadas) y contra el propio código.

**a) `REGISTRAR_GESTION_COBRANZA` de la cajera: la matriz dice NINGUNO, el Doc. 00 §3.1 dice
"X (al cobrar)".** No es un error de implementación — es que la fila "(al cobrar)" se resuelve
por **otro camino**: `ServicioCobranza.confirmarCobro`, al generar un pago parcial, inserta una
gestión `PAGO_PARCIAL` llamando directo a `gestionCobranzaDAO.insertar` (línea ~793), sin pasar
por `Permiso.REGISTRAR_GESTION_COBRANZA` — queda autorizado porque todo el flujo ya pasó por
`Permiso.COBRAR` (TOTAL para la cajera). El resultado final coincide con el Doc. 00 (la cajera sí
"registra" esa gestión puntual, nunca una manual en `/mora`), pero la celda de la matriz de
código no refleja ese "X" — alguien que solo lea `ServicioAutorizacion` concluiría que la cajera
no tiene ningún vínculo con gestiones de cobranza, lo cual es la mitad de la verdad.

**b) La cajera ve el expediente completo del cliente (ingresos, gastos, deudas, referencias),
aunque no lo necesita para cobrar — ver el detalle en §6g de este mismo análisis** (hallazgo ya
anotado en `LEEME.md`, confirmado acá con cita exacta de archivo/línea).

**c) `ENTREGAR_RECIBIR_EFECTIVO_CAJA` da TOTAL a gerente Y a cajera por igual en la matriz, pero
el Doc. 00 distingue "Gerente (confirma)" / "Cajero (registra)".** La separación real no está en
`ServicioAutorizacion` (ahí los dos son TOTAL) sino en una **segunda capa**, dentro de
`ServicioCaja.registrarEntregaGerente`, que rechaza con `NegocioException` si
`perfilUsuario != GERENTE_ADMINISTRADOR` — la cajera pasa el filtro de ruta pero el servicio la
frena igual. Correcto en el resultado, pero la matriz de permisos por sí sola no lo muestra:
hace falta leer el servicio para saber que hay una regla más fina ahí adentro.

**d) El menú muestra un solo enlace "Reportes"; el servidor filtra 21 sub-informes distintos
para el oficial, con una lista que no está en `FiltroAutorizacion` ni en el menú.** Visto desde
afuera (sin leer `ControladorReportesServlet`), nada en `menu.jsp` ni en `RUTAS_PROTEGIDAS`
explica por qué el oficial entra a `/reportes/vencimientos` pero no a `/reportes/cobros` —
funciona (RNF11 se cumple, hay 403 real), pero la "fuente de la verdad" de **qué puede ver** el
oficial queda partida en dos lugares (`VER_REPORTES` + `SUBREPORTES_OFICIAL`), no en uno solo.

**e) La cajera tiene acceso real de servidor a `POST /reversiones/solicitar`
(`SOLICITAR_REVERSION = TOTAL`), pero "Reversiones" aparece con candado en su menú.** No es un
error: la cajera solicita una reversión con un botón dentro de la ficha de `/cobranza/{id}`
(código ya existente, comentario propio: *"la cajera solicita una reversión desde el botón en la
propia ficha de cobranza, no tiene esta pantalla en el menú"*), no navegando al módulo
"Reversiones" (que es la bandeja de autorización, exclusiva del gerente). Documentado acá porque
es exactamente el tipo de asimetría que el punto 2 del pedido de Joel busca: una ruta con acceso
real para un perfil que no tiene ningún enlace de menú apuntando ahí.

**f) `VER_PANEL_REPORTES` y `CONSULTAR_AUDITORIA` están definidos en la matriz, con valores
reales por perfil, pero ningún punto del código los lee.** Confirmado por `grep` en todo
`src/main/java`: las únicas apariciones de `VER_PANEL_REPORTES` fuera de su propia definición son
los 3 `.put(...)` de `ServicioAutorizacion` y un comentario; `CONSULTAR_AUDITORIA` lo mismo. En
la práctica: `/panel` no tiene entrada en `RUTAS_PROTEGIDAS` (accesible a cualquier autenticado;
`ControladorPanelServlet` decide qué tarjetas mostrar ramificando directo sobre `PerfilUsuario`,
sin pasar por este permiso); `/reportes/auditoria` cae bajo el permiso general `VER_REPORTES` más
la whitelist `SUBREPORTES_OFICIAL` (§6d) — nunca bajo `CONSULTAR_AUDITORIA`. El comportamiento
final coincide con el Doc. 00 (panel para todos con diferencias; auditoría solo gerente/admin),
pero por un camino distinto del que la propia matriz sugiere. Si algún día una ruta nueva
intentara protegerse agregando una entrada con estos dos permisos en `RUTAS_PROTEGIDAS`, SÍ
funcionaría (el mecanismo en sí es correcto) — hoy simplemente no hay ninguna ruta que los use.

**g) Pantallas concretas donde se ve el expediente completo (detalle de §6b, pedido explícito de
Joel en la tarea)**: `WEB-INF/vistas/clientes/ficha.jsp` es un único formulario, el mismo para
alta/edición/consulta — `puedeEditarFicha` (= `REGISTRAR_EDITAR_CLIENTE == TOTAL`) solo cambia
`readonly`/`disabled` en los campos, **nunca oculta una sección completa**. Así, "Trabajo e
ingresos" (línea ~421), "Otros ingresos mensuales", "Gastos fijos mensuales", "Cuota mensual de
otras deudas" (líneas ~493-515) y las dos referencias personales obligatorias (líneas ~520-545)
se renderizan igual para la cajera (que solo tiene CONSULTA) que para el oficial (que tiene
TOTAL). Existe el patrón contrario en el mismo archivo: la pestaña de documentos/buró **sí** se
oculta del todo con `if (!esNueva && puedeVerDocumentosBuro)` (líneas 109/571/963) cuando el
perfil no tiene `CARGAR_DOCUMENTOS_CONSULTA_BURO` — la cajera no ve esa pestaña. La asimetría es
que ese mismo patrón de ocultar-por-sección no se aplicó a la caja de ingresos/gastos/deudas/
referencias.

**¿Qué necesita la cajera para cobrar, en los hechos?** La pantalla que de verdad usa para
cobrar, `WEB-INF/vistas/cobranza/ficha.jsp`, muestra del cliente solo `getNombres()` +
`getApellidos()` + `getCedulaIdentidad()` (línea 91-92) — nada de ingresos, gastos, deudas ni
referencias. El resto del expediente (todo lo que sí ve si entra a `/clientes/{id}`) no aparece
en ningún punto de su flujo de cobro real.

**h) "Califica: puede continuar con el registro" en la ficha de cliente (detalle de §6b,
segundo hallazgo de la tarea)**: mismo archivo, mismo mecanismo — un bloque de JavaScript
(`clientes/ficha.jsp`, función autoejecutada ~línea 748) evalúa en vivo 4 campos de la
precalificación (fecha de nacimiento, departamento, situación laboral, comprobante de ingresos)
y pinta el indicador `#indicadorPrecalificacion` en verde ("Califica..."), amarillo (incompleto)
o rojo ("No califica..."). El bloque solo se corta si falta algún elemento del DOM
(`if (!campoFecha || ...) { return; }`) — como esos campos **siempre están presentes** (ocultos
nunca, solo `readonly` para quien no puede editar), el script corre igual para la cajera y pinta
el mismo indicador que vería un oficial dando de alta un cliente real. Para la cajera no tiene
ninguna acción asociada (no puede tocar "Imprimir requisitos" de forma útil ni enviar el
formulario), es solo texto que aparece sin que su perfil pueda hacer nada con él.

## 7. Decisiones

**13/10/2026, Joel aprobó el análisis y probó en pantalla el estado actual**: en oficial, cajera
y gerente, las URL escritas a mano dieron "Acceso restringido" en todos los casos probados
(`/aprobaciones`, `/caja`, `/reportes/cobros`, `/solicitudes`, `/reportes`, `/usuarios`,
`/desembolso`); Parámetros abre en consulta para el gerente; el oficial ve exactamente 5
informes — **RNF11 cumple**. Se confirmaron en pantalla los 2 hallazgos de la cajera (ficha
completa del cliente, §6b/§6g; "Califica", §6h).

**D1 — Menú (RF38 literal: "mostrar SOLO las opciones que corresponden a su perfil")**: las
opciones que el perfil no puede usar no se muestran (se quitan los candados); si una sección
queda sin ninguna opción para ese perfil, desaparece entera (ej.: Aprobación, Seguridad e
Informes para la cajera). El administrador no cambia (D7: TOTAL en los 18 permisos, nunca
pierde una opción). El servidor sigue exactamente igual — es un cambio solo visual.
**Implementado** en `menu.jsp`: `opcionMenu`/`opcionMenuSinActiva` devuelven `""` en vez del
`<span>` con candado cuando `!permitido`; `moduloMenu` devuelve `""` (el módulo entero) cuando
`!verModulo`, y salta las opciones vacías al armar el `<ul>` para no dejar un `<li>` suelto —
mismas condiciones booleanas de siempre (`esGerente`/`esOficial`/`esCajero`/`esAdministrador`),
ninguna lógica de permisos nueva. Verificado que nada en `cabecera.jsp`/`pie.jsp` dependía
estructuralmente del `<span>` bloqueado: las reglas CSS que lo referencian (`.opcion-bloqueada`,
`.candado-opcion`, `.candado-modulo`, `.modulo-bloqueado`) simplemente dejan de aplicar (ningún
elemento las usa ya) sin romper nada; el único JS que depende de `nav-link-modulo`
(`pie.jsp`, V3h, el scroll a "Devolución del pagaré") solo corre dentro de `/formalizacion`,
pantalla a la que la cajera nunca llega (403 antes de que ese JS pueda importar).

**D2 — Ficha del cliente, para quien solo tiene CONSULTA en `REGISTRAR_EDITAR_CLIENTE` (hoy,
solo la cajera)**: ve solo Datos personales, Domicilio y Contacto. El bloque Precalificación
completo (indicador "Califica" incluido), Cónyuge, Trabajo e ingresos (empleador, ingreso neto,
otros ingresos, gastos fijos, cuota de otras deudas, "Documentos sugeridos" y la nota de
capacidad de pago), Referencias (las dos) y la leyenda "Los campos marcados con * son
obligatorios" **no se dibujan en el servidor** — mismo patrón que ya ocultaba la pestaña de
documentos/buró (`if (puedeEditarFicha) { ... }` en `clientes/ficha.jsp`, reusando la variable
que ya existía, sin ningún permiso nuevo). El oficial y el gerente (TOTAL) ven la ficha
exactamente igual que antes. Confirmado, leyendo cada bloque de `<script>` de la pestaña, que
los 5 bloques de JS que referencian elementos ahora ausentes (precalificación, edad en vivo,
cónyuge, empleador/documentos sugeridos, ciudad en cascada) ya tenían su propio
`if (!elemento) { return; }` antes de esta tarea — ninguno lanza error con los campos ausentes.
**Efecto secundario encontrado y corregido el mismo día (13/10/2026)**: el cascada de Ciudad
(`Domicilio`) dependía de `selectDepartamento`, que vive adentro de Precalificación — al
ocultarse Precalificación para la cajera, el `<select>` de Ciudad le quedaba siempre en "Elija
el departamento", sin mostrar la ciudad real. **Corregido** en `clientes/ficha.jsp`: dos
variables nuevas (`nombreDepartamentoActualFicha`/`nombreCiudadActualFicha`), calculadas una
sola vez buscando el id de la persona en `listaDepartamentos`/`listaCiudades` (las mismas listas
que ya llegaban a la pestaña, sin ninguna consulta nueva); en `Domicilio`, cuando
`puedeEditarFicha` sigue exactamente el `<select>` en cascada de siempre (oficial/gerente sin
ningún cambio); cuando no, dos `<input readonly>` ("Departamento"/"Ciudad") con esos nombres.
No se dibuja ningún otro dato de Precalificación (solo el nombre del departamento). El guardia
`if (!selectDepartamento || !selectCiudad) { return; }` del script de cascada ya cubría el caso
de `selectCiudad` ausente también (antes solo faltaba `selectDepartamento`): sigue sin lanzar
error.

**El tag `rf37-rf39-probado` (07/10/2026, ver §0b de la sesión) se deja como está** — Joel
confirmó que **no cuenta como prueba de RF38 ni de RF39**: es un checkpoint informal de una
sesión anterior a la disciplina actual de un documento por RF, sin ningún commit propio de RF38
cerca y sin ninguna nota en `AVANCE.md` de qué se probó. El primer recorrido real de RF38 contra
el anteproyecto es el de esta sesión (13/10/2026).

**D3 (§6j) — "Cancelación anticipada" con pantalla/aviso propio.** Implementado: la ruta pasa a
`/cobranza?origen=cancelacion-anticipada` (`menu.jsp`); `cobranza/busqueda.jsp` lee
`request.getParameter("origen")` y, si vale `"cancelacion-anticipada"`, cambia el `<h1>` a
"Cancelación anticipada" y muestra un aviso informativo arriba ("Cancelación anticipada: busque
el préstamo y, en su ficha, toque «Cancelar todo»."). Sin el parámetro (entrada por "Cobranza"),
la pantalla queda exactamente igual que siempre — no se tocó `ControladorCobranzaServlet` ni
`ServicioCobranza`, es una lectura directa del query string en la vista. El resaltado del menú
(`pie.jsp`) usa el mismo patrón que V3h: con el parámetro presente, abre "Cancelación" y marca
"Cancelación anticipada" activa, cierra "Cobranza y mora" y le saca el activo; sin el parámetro,
no corre nada. **Límite aceptado, no corregido** (no estaba pedido): si la cajera busca algo
desde esa pantalla, el formulario de búsqueda envía a `/cobranza` sin el parámetro `origen` — el
aviso y el título desaparecen en el resultado de la búsqueda (la pantalla pasa a verse como
"Cobranza" normal). Si hace falta que el aviso persista durante la búsqueda, es un cambio aparte
(agregar `origen` como campo oculto del formulario).

**D4 (§6j) — "Devolución del pagaré" baja sola, también la primera vez.** Causa real
investigada antes de corregir (pedido de Joel): el salto nativo del navegador al ancla
`#cierre-documental` ocurre apenas el HTML termina de parsear — **antes** de que terminen de
cargar las fuentes web (Montserrat/Source Sans Pro, `<link>` en `cabecera.jsp`) y cualquier
imagen de la página; esas cargas tardías reacomodan el alto del documento (reflow) y corren la
sección fuera de vista, aunque el salto inicial haya sido al lugar correcto. El
`scrollIntoView` que ya existía (V3h) corría en `$(document).ready()` (`DOMContentLoaded`), que
también es **antes** de que terminen de cargar fuentes/imágenes — mismo problema, no lo
resolvía (ambos eventos ocurren demasiado temprano). **Corregido**: el mismo `scrollIntoView`
ahora corre en `window.addEventListener('load', ...)` (que sí espera a que todo termine de
cargar), dentro del mismo bloque de `pie.jsp`, sin tocar el resaltado del menú (que no depende
de la carga de recursos, se queda en `$(document).ready()`) ni el `scroll-margin-top` de
`cabecera.jsp` (sigue siendo el que evita que la barra superior fija tape la sección). Al entrar
por "Formalización" (sin el hash), el guardia existente sigue sin ejecutar nada — la pantalla
abre arriba, como siempre.

**D5 (§6i) — sin asteriscos en consulta.** Las 10 etiquetas de "Datos personales"/"Domicilio"/
"Contacto" que D2 deja visibles para la cajera (`Cédula de identidad`, `Vencimiento de la
cédula`, `Nombres`, `Apellidos`, `Estado civil`, `Barrio`, `Dirección (calle y número)`, `Tipo de
vivienda`, `Antigüedad en el domicilio (años)`, `Teléfono/WhatsApp`) ahora llevan
`<%= puedeEditarFicha ? " *" : "" %>` en vez del `" *"` fijo — con `puedeEditarFicha` (oficial/
gerente) no cambia nada. Las etiquetas de Cónyuge y el "Ciudad *" del `<select>` en cascada no
hizo falta tocarlas: ya están enteramente dentro de un `if (puedeEditarFicha)` desde D2/D2-bis,
así que su "*" ya solo aparece cuando corresponde.

## 8. Historial de cambios

| Fecha | Cambio | Commit | Tag |
|---|---|---|---|
| — | Implementado incrementalmente desde E2 (D7, D-151) hasta Bloque 7/E10 (menú con candados, V3b/V3c). | — | — |
| 13/10/2026 | Primer análisis contra el anteproyecto (este documento), sin cambios de código ni de base. | — | — |
| 13/10/2026 | Joel probó en pantalla el estado actual (RNF11 cumple, hallazgos de la cajera confirmados) y aprobó D1/D2. | — | — |
| 13/10/2026 | D1 (menú sin candados) y D2 (ficha de cliente sin secciones para consulta) implementados — `menu.jsp`, `clientes/ficha.jsp`. `mvn test`: 724/724 en verde. | — | — |
| 13/10/2026 | Corregido el efecto secundario de D2 (Ciudad vacía para la cajera) — `clientes/ficha.jsp` muestra Departamento/Ciudad como texto en consulta. Confirmado que la leyenda de obligatorios ya quedaba oculta. `mvn test`: 724/724 en verde. | — | — |
| 13/10/2026 | Probado en pantalla por Joel, después de su Clean and Build (recorrido D1/D2). | `61d0e5e` | — |
| 13/10/2026 | D3 (Cancelación anticipada con pantalla/aviso propio), D4 (Devolución del pagaré baja sola, causa real investigada y corregida, ver nota abajo) y D5 (sin asteriscos en consulta) implementados — `menu.jsp`, `cobranza/busqueda.jsp`, `pie.jsp`, `clientes/ficha.jsp`. `mvn test`: 724/724 en verde. | — | — |
| 13/10/2026 | D3/D4/D5 probados en pantalla y aprobados por Joel, después de su Clean and Build, ver §9. Tag `rf38-probado` movido a este commit. | `d0f8dc6` | `rf38-probado` (movido) |
| 13/10/2026 | 2 textos menores: aviso de "Cancelación anticipada" sin repetir el título (D3); "(opcional)" también oculto en consulta en RUC/Referencia del domicilio/Teléfono alternativo/Correo electrónico (D5) — `cobranza/busqueda.jsp`, `clientes/ficha.jsp`. `mvn test`: 724/724 en verde. | — | — |
| 13/10/2026 | Textos menores probados en pantalla y aprobados por Joel, después de su Clean and Build, ver §9. Tag `rf38-probado` movido a este commit (estado probado completo, D1 a D5 + textos). | *(este commit)* | `rf38-probado` (movido) |

**Causa real de D4, investigada antes de corregir (pedido de Joel)**: "Devolución del pagaré"
apunta a `/formalizacion#cierre-documental`. El navegador intenta el salto nativo al ancla
**apenas termina de parsear el HTML** — antes de que terminen de cargar las fuentes web
(Montserrat/Source Sans Pro, `<link>` en `cabecera.jsp`) y cualquier imagen de la página. Esas
cargas tardías reacomodan el alto del documento (reflow) y corren la sección fuera de vista,
aunque el salto inicial haya apuntado al lugar correcto — por eso "la página abría arriba". El
`scrollIntoView` que ya existía (V3h, pie.jsp) no lo arreglaba porque corría en
`$(document).ready()` (evento `DOMContentLoaded`), que **también** es anterior a que terminen de
cargar fuentes/imágenes — mismo problema, momento equivocado. **Corrección**: el mismo
`scrollIntoView` se movió a `window.addEventListener('load', ...)` (evento que sí espera a que
termine de cargar todo), sin tocar el resaltado del menú (no depende de la carga de recursos,
se queda en `$(document).ready()`) ni el `scroll-margin-top` de `cabecera.jsp` (que sigue
evitando que la barra superior fija tape la sección). **Confirmado en pantalla por Joel el mismo
día** (ver §9): baja sola también la primera vez.

## 9. Pruebas realizadas en pantalla

**13/10/2026, Joel (oficial/cajera/gerente, `sgcpc_db_pruebas`), recorrido inicial (antes de
D1/D2)**: las URL escritas a mano dieron "Acceso restringido" en todos los casos probados;
Parámetros en consulta para el gerente; el oficial ve exactamente 5 informes — RNF11 cumple.
Confirmados los 2 hallazgos de la cajera (ficha completa, "Califica"), ver §7.

**13/10/2026, Joel, después del Clean and Build, tag `rf38-probado`**:

- **Menú por perfil (D1)**: administrador ve todo; gerente, oficial y cajera ven solo sus
  opciones; los módulos sin ninguna opción para el perfil desaparecen enteros; no queda ningún
  candado en ningún perfil.
- **URL escritas a mano** (servidor sin cambios, ya correcto desde antes de D1/D2) → "Acceso
  restringido": `/aprobaciones`, `/caja`, `/reportes/cobros` (oficial); `/solicitudes`,
  `/reportes`, `/usuarios` (cajera); `/usuarios`, `/desembolso` (gerente). `/parametros` abre en
  consulta para el gerente.
- **Reportes del oficial**: 5 de 21, confirmado.
- **Ficha de cliente (D2)**: la cajera ve solo Datos personales, Domicilio (Departamento y
  Ciudad reales en solo lectura, corrección de hoy) y Contacto — sin Precalificación, Cónyuge,
  Trabajo e ingresos, Referencias ni la leyenda de obligatorios. El oficial ve la ficha
  exactamente igual que antes.

**13/10/2026, Joel, después de su Clean and Build, D3/D4/D5 aprobados**:

- **D3 (cajera)**: "Cancelación anticipada" muestra su propio título ("Cancelación anticipada"),
  el aviso informativo y el menú marca "Cancelación anticipada" (no "Cobranza"); "Cobranza"
  sigue exactamente igual que siempre.
- **D4 (oficial)**: "Devolución del pagaré" desde el menú baja sola hasta "Préstamos finalizados
  — cierre documental pendiente", también la primera vez; "Formalización" abre arriba, como
  siempre.
- **D5**: la ficha de la cajera no tiene ningún asterisco; la del oficial sigue con asteriscos,
  Precalificación y "Guardar cambios" — sin cambios.

**13/10/2026, Joel, después de su Clean and Build, textos menores aprobados**:

- El aviso de "Cancelación anticipada" dice solo "Busque el préstamo y, en su ficha, toque
  «Cancelar todo»." (ya no repite el título).
  - **(15/11/2026, B198)** Ese texto cambió: el botón se llama **«Todo el préstamo»** desde B106
    (05/11/2026), así que el aviso mandaba a tocar algo que ya no existía con ese nombre. Ahora
    imprime el rótulo del **mismo dueño que el botón**
    (`ServicioCobranza.OPCION_TODO_EL_PRESTAMO`), para que un renombre futuro no vuelva a dejarlo
    atrás. La parte de "ya no repite el título" **sigue valiendo** y es lo que cierra el pendiente
    que `RF26 §10` todavía listaba (ver esa sección, revisada el mismo día).
- En la ficha de solo consulta de la cajera ya no aparece "(opcional)" en RUC, Referencia del
  domicilio, Teléfono alternativo ni Correo electrónico.

## 10. Pendientes y trabajo futuro

**De §6 (hallazgos del análisis, sin implementar, a la espera de que Joel decida)**:

- **§6a**: la celda `REGISTRAR_GESTION_COBRANZA` de la cajera en `ServicioAutorizacion` no
  refleja el "X (al cobrar)" del Doc. 00 — evaluar si vale la pena anotarlo con un comentario más
  explícito en el propio código (ya tiene uno, podría citar el Doc. 00 directamente).
- **§6c**: la separación "gerente confirma / cajera registra" de la entrega de efectivo vive
  solo en `ServicioCaja`, no en la matriz de permisos — evaluar si documentar mejor alcanza, o si
  conviene reflejarlo con un permiso propio.
- **§6d**: la whitelist `SUBREPORTES_OFICIAL` (5 de 21 informes) vive solo en
  `ControladorReportesServlet`, invisible desde `FiltroAutorizacion` y desde el menú — evaluar
  si vale la pena acercarla a la matriz central (sin necesariamente moverla).
- **§6e**: `SOLICITAR_REVERSION` de la cajera sin ningún enlace de menú — ya documentado como
  intencional (botón contextual en `/cobranza/{id}`), sin acción pendiente salvo dejarlo escrito
  acá para que no se lea como un agujero.
- **§6f**: `VER_PANEL_REPORTES` y `CONSULTAR_AUDITORIA` definidos pero nunca leídos — evaluar si
  conviene borrarlos (si de verdad no sirven para nada) o efectivamente cablearlos a `/panel` y
  `/reportes/auditoria` en `FiltroAutorizacion` (consolidaría el mecanismo en un solo lugar en
  vez de dos: `PerfilUsuario` directo en el controlador + whitelist aparte).
- **§6i — corregido y probado por D5 (13/10/2026, incluidos los textos menores)**: ver §7/§9.
- **§6j — corregido y probado por D3/D4 (13/10/2026, incluido el texto menor del aviso)**: ver
  §7/§9.

RF38 queda sin pendientes propios de D1-D5; lo que sigue abierto son los hallazgos de §6a/§6c/
§6d/§6e/§6f (de bajo riesgo, documentados, a la espera de que Joel decida si vale la pena
tocarlos).

## 11. Guion para la defensa

"RF38 exige mostrar a cada usuario solo las opciones y los informes de su perfil; RNF11 exige
que el servidor lo controle igual aunque se escriba la URL a mano. Los dos se cumplen con el
mismo mecanismo centralizado, `FiltroAutorizacion` + `ServicioAutorizacion` (18 permisos, una
sola matriz en memoria), probado en pantalla el 13/10/2026: con los 4 perfiles, cada URL
escrita a mano por fuera de lo permitido dio 'Acceso restringido', y el oficial vio exactamente
los 5 de 21 informes que le corresponden. El menú (`menu.jsp`) nunca decide nada — solo refleja,
con las mismas condiciones de perfil, lo que el servidor ya permite: desde D1, una opción sin
permiso directamente no se dibuja (antes quedaba con candado), y un módulo sin ninguna opción
visible desaparece entero. Además, RF38/D2 resolvió un hallazgo de datos sensibles: la cajera
(con consulta, no edición, sobre la ficha del cliente) ya no ve ingresos, gastos, deudas ni
referencias — solo lo que necesita para identificar al cliente (datos personales, domicilio,
contacto), con el mismo mecanismo de ocultar en el servidor que ya usaba la pestaña de
documentos/buró. Los hallazgos que quedan abiertos (§6, §10) son de legibilidad de la matriz (datos repartidos en
más de un lugar, ej. la whitelist de informes del oficial) y de navegación del menú, nunca de
que el servidor deje pasar algo que no debería."
