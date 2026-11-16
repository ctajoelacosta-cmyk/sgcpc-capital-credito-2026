# Guía visual del SGCPC

Describe lo que **ya está implementado** (login, barra lateral, barra superior y
`sgcpc-tema.css`), con los valores reales del código — no son recomendaciones aparte, son los
números que ya usan `acceso.jsp`, `plantilla/cabecera.jsp`, `plantilla/menu.jsp` y
`recursos/css/sgcpc-tema.css`. Sirve como referencia para no reinventar un color o una medida
cada vez, y como checklist para cualquier pantalla nueva o modificada (sección 7).

**Regla general: no se usan colores, tipografías ni medidas fuera de lo que describe esta
guía.** Si una pantalla nueva necesita algo que no está acá, se agrega a la guía primero (o se
pregunta a Joel), no se inventa suelto en un `.jsp`.

## 1. Paleta

Los tres colores base son del logo. Todo lo demás (grises, rojo, ámbar, y los tonos joya del
Panel que agregó B110) es de apoyo.

### 1.1 Colores de marca y de apoyo

| Uso | Color | Dónde se usa |
|---|---|---|
| Azul (marca) | `#0C325C` | Fondo de la barra lateral, **fondo de la barra superior (B110)**, **encabezado de tablas (B110)**, títulos `h2`-`h5`, `.btn-primary`, activo de paginación |
| Azul, hover/oscuro | `#0A2A4E` | Hover de `.btn-primary`, zona de la marca (franja del logo en la barra lateral), **títulos `h1` y saludo del Panel (B110)** |
| Verde (marca) | `#1B9B6A` | `.btn-success`, anillo de foco de los formularios, barrita del ítem activo del menú, botón "Ingresar" del login, **franja bajo la barra superior, borde superior de los recuadros y barrita de los subtítulos del Panel (B110)** |
| Verde, hover/oscuro | `#14724E` | Hover de `.btn-success`, texto de `.btn-info`/`.alert-info`/`.badge-info`, hover del botón de login |
| Verde claro | `#34D399` | Ícono del módulo abierto en el menú lateral, barrita/ícono del ítem "dónde estoy", **perfil en la tarjeta de usuario del pie del menú lateral (B111)**, **anillo de foco de sus dos botones (B111)** |
| Gris, fondo de pantalla | `#EEF3F8` | **Fondo del área de contenido (B110)** — reemplaza al `#f4f6f9` de AdminLTE |
| Gris, fondos suaves | `#F1F5F9` | Fondo del botón deshabilitado (B87) |
| Gris, bordes | `#E2E8F0` | Línea bajo los subtítulos, borde superior del pie de página |
| Gris, bordes de B110 | `#C3D0DD` / `#B9C7D6` / `#D5DEE8` / `#E1E8F0` | Borde de los recuadros, borde de la tabla, líneas entre filas y entre columnas |
| Gris, texto secundario | `#475569` | Labels de formulario, texto inactivo del menú lateral (`#E2E8F0` ahí, ver más abajo) |
| Gris, texto terciario | `#64748B` / `#334155` | Texto del botón deshabilitado (B87), labels |
| Claros sobre la barra azul | `#D7E2EE` | **Fecha del día, en la barra superior (B110)** |
| Claro sobre la barra lateral | `#E2E8F0` | **Botón "Contraseña" del pie del menú lateral, con borde `rgba(255,255,255,.45)` (B111)** |
| Verde muy claro | `#E9F5EF` / `#C9E6D7` / `#E3F4EC` | **Encabezado de recuadro, su borde inferior, y fila de tabla resaltada al pasar el mouse (B110)** |
| Azul muy claro | `#F2F6FB` | **Cebra de las tablas (B110)** |
| Rojo (peligro) | `#D64545` / `#C0392B` | `.btn-danger`, `.btn-outline-danger`, `.alert-danger` |
| Rojo del botón "Salir" | `#B91C1C` (hover `#991B1B`) | **Botón "Salir" del pie del menú lateral (B111)** — el mismo rojo de la tarjeta de mora del Panel |
| Ámbar (aviso) | `#F0AD2E` / `#E6A700` / `#8a5a00` | `.btn-warning`, `.alert-warning`, píldora "BASE DE PRUEBAS" |

**Regla: el rojo y el ámbar se usan solo para peligro/advertencia** (acciones irreversibles,
avisos, la base de pruebas) — nunca como color decorativo o de marca.

El login (`acceso.jsp`) usa su propia variación de azules para el fondo degradado
(`#134477`/`#071F3B`) y tonos de gris neutro de Bootstrap (`#6C757D`, `#D1D5DB`) para elementos
que no llevan color de marca (placeholder, texto de ayuda) — mismo criterio, distinta pantalla.
**B110 no lo toca**: el login no incluye `sgcpc-tema.css`.

### 1.2 Tonos de las tarjetas del Panel (B110, "opción F", aprobada por Joel el 05/11/2026)

Cada tarjeta del Panel es de color pleno con texto blanco, con un degradado de 135° (el extremo
oscuro arriba a la izquierda, el claro abajo a la derecha). **El tono lo decide la tarjeta, no su
posición**: cada una lleva su clase en `panel.jsp` y la clase vive en `sgcpc-tema.css` §B110.3.
El Panel arma filas distintas para cada perfil, así que asignar el color por posición haría que
el mismo color significara cosas distintas según quién entre.

| Clase | Degradado | Tarjeta |
|---|---|---|
| `panel-tarjeta-azul` | `#0C325C → #1D4E89` | Cobros de hoy |
| `panel-tarjeta-cian` | `#155E75 → #0E7490` | Vencen hoy |
| `panel-tarjeta-violeta` | `#5B21B6 → #7C3AED` | Promesas de hoy |
| `panel-tarjeta-frambuesa` | `#9D174D → #BE185D` | Efectivo en caja · Diferencias de caja |
| `panel-tarjeta-indigo` | `#3730A3 → #4338CA` | Pendientes de aprobar (gerente) · Aprobadas para formalizar (oficial) · Cobrado esta semana |
| `panel-tarjeta-rojo` | `#991B1B → #B91C1C` | **Préstamos en mora, y nada más** |
| `panel-tarjeta-petroleo` | `#134E4A → #0F766E` | Cartera en la calle |
| `panel-tarjeta-esmeralda` | `#0F5C3F → #157A3A` | Cobrado este mes |
| `panel-tarjeta-azulacero` | `#075985 → #0369A1` | Pendientes de desembolso · Mis solicitudes en curso (oficial) |
| `panel-tarjeta-ciruela` | `#701A75 → #A21CAF` | Reversiones por autorizar · Pagarés por devolver (oficial) |
| `panel-tarjeta-gris` | `#475569 → #5B687C` | "Sin caja abierta" (estado apagado) |

**Reglas que no se rompen:**

- **El rojo es solo la mora.** Ninguna otra tarjeta lo usa. Su extremo claro se oscureció a
  `#B91C1C` para que no se confunda con la frambuesa de la caja: ladrillo contra magenta.
- **Un tono se repite solo entre tarjetas que nunca se ven juntas** (una es del gerente y la
  otra del oficial) o **en bloques distintos**. Dentro de un mismo bloque, ningún perfil ve dos
  tarjetas del mismo tono.
- **El contraste se mide contra el extremo claro Y con el brillo del hover aplicado**
  (`brightness(1.08)`), que es el peor caso real. Todos los tonos quedan en **4,74:1 o más**
  sobre blanco. Dos tonos de la propuesta original se bajaron un escalón justamente por esto:
  esmeralda `#15803D → #157A3A` (daba 4,42:1 en hover) y gris `#64748B → #5B687C` (4,19:1).
  **Cualquier tono nuevo se mide igual antes de entrar acá.**

### 1.3 Movimiento al pasar el mouse (B110)

La tarjeta del Panel se levanta 6px y crece un 2%, con más sombra y `brightness(1.08)`; el ícono
crece y gira −6°; la flecha de "Ir a…" se corre 4px. Las filas de tabla y los recuadros
acompañan con una transición suave. **`prefers-reduced-motion: reduce` apaga las transiciones y
los desplazamientos** (la sombra y el brillo quedan: son color, no movimiento).

**La tarjeta no lleva `cursor: pointer`** (decisión de Joel, 05/11/2026): la tarjeta entera no es
cliqueable —lo cliqueable es el enlace "Ir a…" del pie— y varias tarjetas no tienen enlace. Una
manito sobre algo que no responde al clic promete algo que no se cumple.

## 2. Tipografía

- **Montserrat** (archivo local, `recursos/fuentes/montserrat/`, pesos **600** y **700**, licencia
  OFL — nunca Google Fonts/CDN) para todo lo que es **título, etiqueta, botón o navegación**:
  títulos de pantalla (`h1`-`h5`), encabezados de módulo y opciones del menú lateral, nombre del
  usuario, botones, encabezado de tabla, etiquetas de formulario del login, botón "Ingresar".
- **Source Sans Pro** (la de siempre, también local) para el **texto base y los datos**: el
  contenido de las tablas, los párrafos, los valores de los formularios — nada que lleve
  Montserrat explícito se queda con esta por `font-family` normal de Bootstrap/AdminLTE.
- `font-display: swap` en ambos pesos de Montserrat: el texto se ve de entrada con Source Sans
  Pro y cambia solo cuando termina de cargar, sin bloquear ni romper nada si no carga.

Tamaños reales usados:

| Elemento | Tamaño | Peso |
|---|---|---|
| Título de pantalla (`h1`) | 28px | 700 |
| Subtítulo de sección (`h2`-`h5`, ej. "Datos personales") | 18px | 600 |
| Texto base / tablas | 14px (Bootstrap por defecto) | 400 |
| Botones, ítems del menú lateral (nivel 1) | 14px | 600 |
| Encabezado de módulo del menú, opciones de submenú | 13px | 600-700 |
| Encabezado de tabla | 12-13px, mayúsculas | 600 |
| Nombre de usuario (pie del menú lateral, B111) | 14px | 700 |
| Botones "Contraseña" y "Salir" del pie (B111) | 13px | 600 |
| Perfil de usuario en el pie (B111) | 12,5px | 600 |
| Candados, etiquetas de campo del login | 11px | 600 |
| Pie de página | 12px | 400 |

## 3. Formas y profundidad

Radios realmente usados en el código (no se inventan valores nuevos sin necesidad):

| Radio | Dónde |
|---|---|
| 3px | Pulgar de la barra de scroll de la barra lateral |
| 8px | Botones, campos de formulario, ítems del menú lateral, íconos del login |
| 10px | Recuadro de cada módulo del menú lateral |
| 12px | **Tarjeta de usuario del pie del menú lateral (B111)**; sus dos botones, 8px |
| 12px | Tarjetas (`.card`) |
| 20-22px | Tarjeta del login y su borde luminoso (pantalla "hito", con más presencia que el resto) |
| 25px | Botón "Ingresar" del login (altura 50px ÷ 2 = píldora completa) |
| 50% | Círculos (iniciales de usuario, ícono de usuario de la tarjeta lateral, punto de la píldora) |
| 999px | Píldoras (badges, "BASE DE PRUEBAS") |

Sombras reales (siempre con el azul o el verde de marca en el `rgba`, nunca negro puro salvo el
login, que es una pantalla con más profundidad a propósito):

| Sombra | Uso |
|---|---|
| `0 2px 8px rgba(12,50,92,.08)` | Barra superior |
| `0 2px 10px rgba(12,50,92,.06)` | Tarjetas (`.card`) |
| `0 4px 10px rgba(12,50,92,.15)` | Botones al pasar el mouse |
| `0 0 12px rgba(27,155,106,.25)` | Hover de un ítem del menú lateral |
| `0 0 10px rgba(27,155,106,.18)` | Módulo "dónde estoy" (permanente, no solo hover) |
| `0 25px 50px -12px rgba(0,0,0,.35)` + `0 10px 20px -5px rgba(27,155,106,.15)` | Tarjeta del login (reposo) |
| `0 30px 60px -12px rgba(0,0,0,.45)` + el mismo verde | Tarjeta del login (hover, un poco más de elevación) |

Bordes: 1px, siempre en gris `#E2E8F0`/`#CBD5E1` o transparente (para no mover el contenido al
agregar color en hover/activo — ver interacción). Espacios: `gap`/`padding` chicos (6-10px) entre
ícono y texto, 16-32px entre bloques grandes (padding de tarjetas, aire sobre el `h1`).

## 4. Interacción

- **Hover**: los botones suben 1px (`translateY(-1px)`) con sombra leve; el campo del login y su
  tarjeta suben 2-4px; los ítems del menú lateral y las pestañas cambian a fondo verde suave
  (`rgba(27,155,106,.10-.12)`) sin moverse (por eso llevan `border:1px solid transparent` en
  reposo — el borde de color del hover no empuja el contenido al aparecer).
- **Foco**: anillo verde de **4px** (`box-shadow: 0 0 0 4px rgba(27,155,106,.15)`), igual en el
  login y en `sgcpc-tema.css` (formularios). En los `<a>` de la barra lateral y del ☰, el anillo
  se sacó directo (`:focus{outline:none;box-shadow:none}`, sin condicionar a `:focus-visible`:
  varios navegadores ya consideran "focus-visible" un clic de mouse en un `<a href="#">`, así que
  esa condición no alcanza ahí) — el feedback de teclado queda igual que el hover. En las pestañas
  de contenido (`sgcpc-tema.css`) sí se usa `:focus-visible` para el anillo verde de 3px, porque
  ahí Joel pidió distinguir mouse de teclado explícitamente.
- **Activo / "dónde estoy"**: fondo verde más marcado (`rgba(27,155,106,.30)`), texto blanco en
  negrita, barrita verde de 3px a la izquierda (`border-left`, no un elemento aparte). En las
  pestañas de pills, fondo azul sólido con texto blanco; en tabs, el mismo efecto pero como
  subrayado verde de 3px.
- **Sin permiso**: la opción se atenúa a opacidad **.45**, cursor `not-allowed`, candado chico a
  la derecha y es un `<span>` (nunca un `<a>`: no se puede hacer clic ni llegar con teclado) con
  `title="No disponible para tu perfil"`. El bloqueo real lo sigue haciendo el servidor
  (`FiltroAutorizacion`) — esto es solo para que se vea.
- **Estados de carga**: el botón "Ingresar" pasa a "INGRESANDO…" con un spinner chico y
  `disabled` (evita doble clic), y los campos del formulario pasan a `readonly` (nunca
  `disabled`, que no manda su valor) con opacidad .7 mientras se envía.
- **Validación/avisos**: borde rojo suave + anillo (`0 0 0 3px rgba(220,53,69,.12)`) en un campo
  con error, que se saca apenas el usuario empieza a escribir; los avisos (`.alert`) usan fondo
  claro + borde izquierdo de 4px del color (nunca un cartel sólido agresivo) y, en pantallas
  internas, el resultado de una acción se muestra en un modal claro (SweetAlert2) al cargar la
  página, no en un cartel que puede pasar desapercibido.
- **`prefers-reduced-motion: reduce`**: todas las animaciones/transiciones/transform se apagan
  (burbujas de fondo y spinner del login, punto parpadeante de la píldora de pruebas, elevación
  de tarjetas/botones) — el usuario que lo pide en su sistema operativo nunca ve nada que se
  mueva solo.

## 5. Componentes

- **Botones**: mismas clases de Bootstrap de siempre, mismo significado — solo cambia el color.
  `.btn-primary` (azul, acción principal neutra), `.btn-success` (verde, confirmar/avanzar),
  `.btn-info` (verde azulado sobrio, `#E6F4EF`/`#14724E`/`#B7E4D0` — nunca el celeste eléctrico de
  Bootstrap, no es un color del logo; se usa para "ver/consultar", ej. "Cierre documental"),
  `.btn-danger`/`.btn-warning`/`.btn-outline-danger` (rojo/ámbar, siempre peligro o irreversible).
  Todos: radio 8px, Montserrat 600, elevación de 1px + sombra al pasar el mouse.
- **Tablas** (`.table`): encabezado gris claro (`#F1F5F9`) con texto azul en mayúscula, hover de
  fila muy suave (`#F8FAFC`), bordes finos `#E2E8F0`, rayas de `.table-striped` casi
  imperceptibles (`rgba(241,245,249,.4)`).
- **Tarjetas** (`.card`, nunca `.small-box` del Panel): sin borde duro, radio 12px, sombra suave,
  título del `.card-header` en azul Montserrat 600.
- **Formularios**: `.form-control`/`select` con radio 8px, borde `#CBD5E1`, foco verde (igual que
  el login); `label` en `#334155`, negrita.
- **Avisos** (`.alert`): fondo claro + borde izquierdo de 4px, radio 8px — mismo estilo en el
  login y en el resto del sistema.
- **Badges**: forma de píldora; `.badge-success`/`.badge-primary`/`.badge-info` con los colores de
  marca, `.badge-warning`/`.badge-danger` sin cambio (su rol de aviso/peligro ya es correcto).
- **Píldora "BASE DE PRUEBAS"**: ámbar, punto parpadeante suave, siempre con la misma condición
  del servidor (`FabricaConexionBD.esBaseDePruebas()`) — en el login arriba a la izquierda, en las
  pantallas internas en la barra superior. Nunca decorativa: solo existe si el
  sistema está corriendo contra la base de pruebas.
- **Menú lateral por módulos**: 8 módulos + Panel, colapsados por defecto, cada uno en su propio
  recuadro; candados visibles por perfil (opción u módulo entero sin ningún permiso); modo
  "solo íconos" al colapsar la barra (hover la expande); ancho 280px en escritorio.
- **Barra superior**: azul de marca con franja verde abajo (B110), botón ☰ y fecha del servidor
  en español. **Desde B111 no lleva nada del usuario**: ni nombre, ni desplegable.
- **Tarjeta de usuario al pie del menú lateral (B111)**: fija abajo (el `<aside>` es una columna
  flex y el menú se queda con el alto sobrante), con el círculo verde `#167F57` de iniciales
  (nombre + apellido), nombre, perfil y **dos botones a la vista**, "Contraseña"
  (`/cambiar-contrasena`) y "Salir" (`/salir`). Con la barra **colapsada** queda solo el círculo,
  centrado; el texto y los botones se ocultan con `display:none` —nunca con opacidad, que dejaría
  un "Salir" invisible pero clickeable— y vuelven al pasar el mouse o al expandirla.

## 6. Leyes de UX aplicadas

| Ley | Qué dice | Ejemplo concreto en el SGCPC |
|---|---|---|
| **Jakob** | El usuario espera que un sistema funcione como los que ya conoce | La barra lateral con íconos, el usuario arriba a la derecha y las tablas con hover son el patrón estándar de cualquier panel administrativo (AdminLTE/Bootstrap) — nadie tiene que aprender una interacción inventada |
| **Fitts** | Cuanto más grande y cerca está un objetivo, más rápido se acierta | El botón ☰ tiene un área de clic de 40×40px (no solo el ícono de 16px); el botón "Ingresar" del login ocupa el ancho completo de la tarjeta |
| **Hick** | Más opciones = más tiempo para decidir | Las ~20 pantallas del sistema se agrupan en 8 módulos colapsados en el menú lateral — el usuario elige primero entre 8 categorías, no entre 20+ opciones sueltas |
| **Miller** | La memoria de trabajo maneja bien grupos de 7±2 ítems | Cada módulo del menú agrupa 2 a 4 opciones (ej. "Cancelación": Cancelación anticipada + Devolución del pagaré), nunca una lista larga suelta |
| **Proximidad / Región común** | Lo que está agrupado visualmente se percibe como relacionado | Cada módulo del menú lateral vive en su propio recuadro con fondo y borde — se distingue de los demás módulos sin necesidad de leer las etiquetas |
| **Von Restorff** | El elemento distinto en un grupo se recuerda/destaca | La píldora ámbar "BASE DE PRUEBAS" es el único elemento con esos colores en toda la barra superior/login, a propósito, para que nunca se confunda con el sistema real |
| **Estético-usabilidad** | Lo que se percibe más lindo se percibe (aunque no sea objetivamente cierto) más fácil de usar | El rediseño visual (V1-V7) no cambió ningún flujo ni lógica — mismos botones y pantallas de siempre, solo con mejor tipografía/color/espaciado — y aun así cada etapa se probó como una mejora clara |
| **Doherty** | Una respuesta en menos de 400ms mantiene la atención del usuario | El botón de login cambia a "INGRESANDO…" con spinner de inmediato al hacer clic, y los campos pasan a solo lectura — confirmación instantánea de que el clic funcionó |
| **Postel** | Ser tolerante con lo que se acepta del usuario | El formulario de ingreso se puede enviar haciendo clic en "Ingresar" o presionando Enter en cualquier campo — no exige un único camino |
| **Tesler** | La complejidad inherente no se elimina, solo se mueve de quién la maneja | El cálculo de cuotas/mora/aplicación de pagos es complejo (`ServicioMotorFinancieroTest`, decenas de casos) pero la pantalla solo muestra un resultado simple en guaraníes, con el texto en letras — la complejidad vive en el motor financiero, nunca en el JSP |
| **Similitud** | Elementos que se ven iguales se interpretan con la misma función | Todo botón de riesgo (anular, revertir, rechazar) es rojo en cualquier pantalla del sistema; todo botón de confirmación positiva es verde — el color se generaliza, no hace falta leer el texto cada vez |
| **Pico-final** | Se recuerda el pico emocional y el final de una experiencia | Al terminar una acción importante (aprobar, desembolsar, cobrar) el sistema siempre cierra con un modal claro de éxito (SweetAlert2), nunca un simple refresh silencioso — el final del flujo queda como un momento claro y positivo |

## 7. Reglas para pantallas nuevas o modificadas

Checklist antes de dar por terminado cualquier cambio visual:

1. **Solo visual**: nunca cambia Java, servlets, filtros, permisos, rutas, cálculos ni datos en el
   mismo paso que un cambio de estilo (si hace falta tocar lógica, es un paso aparte, explicado y
   con el OK de Joel).
2. **Colores de la paleta de la sección 1** — nunca un color nuevo suelto en un `.jsp`. Si falta
   un caso, se agrega a esta guía primero.
3. **Una acción principal por pantalla** (`.btn-primary` o `.btn-success`, bien visible) — el
   resto de las acciones, secundarias (`btn-outline-secondary`, `btn-info` para "ver/consultar").
4. **Acciones peligrosas o irreversibles** (anular, revertir, rechazar, eliminar) siempre
   separadas visualmente de las demás y en rojo (`.btn-danger`/`.btn-outline-danger`), nunca
   mezcladas entre botones neutros.
5. **Buscador en toda lista que pueda crecer** (clientes, préstamos, solicitudes, reportes) — un
   campo de búsqueda simple arriba de la tabla, siempre con el mismo lugar/estilo.
6. **Mensajes claros, en español, sin jerga técnica** — nunca un mensaje de error de Java/SQL
   crudo en pantalla; el patrón ya existente (modal de SweetAlert2 con título corto + detalle) es
   el que se reutiliza, no uno nuevo por pantalla.
