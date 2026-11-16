# Manual de usuario — qué quedó pendiente de actualizar (informe del 16/11/2026)

**Informe en solo lectura, pedido por Joel el 16/11/2026 al cerrar la fase 2.** No se tocó ningún
archivo del manual. Joel decidió el mismo día **no actualizarlo todavía** (la prioridad pasó a la
entrega del libro), así que esto queda acá para retomarlo.

El manual vigente son los **8 archivos de esta carpeta** (`01_cotizador` … `08_cobranza`),
**1.889 líneas de Markdown**, con fechas del **22/10 al 03/11/2026** — o sea, **todos anteriores
al 05/11**, que es cuando empezó a aplicarse la fase 2. **Ninguno tiene imágenes**: la primera
línea de los ocho dice *"Las capturas de pantalla las agrega Joel en el manual visual"*, y ese
manual visual **no existe** todavía (buscado en el repositorio y en la carpeta de al lado).

## A. Texto que hoy miente (5 lugares, 1 hora de trabajo)

| Dónde | Dice | La pantalla hace |
|---|---|---|
| `05` §*El Panel* | bloques **Hoy → Cartera → Pendientes** | **Hoy → Pendientes → Cartera** (`panel.jsp:142/271/412`, **B228**) |
| `05:15` | *"Cobros de hoy"* | **"Cobros de hoy, aplicado a la deuda (… Gs.)"** (**B107**, 07/11) |
| `05:31-32` | *"Cobrado esta semana / este mes"* | las dos llevan **"(aplicado a la deuda)"** (**B107**) |
| `07:25` | *"Saldo teórico actual"* | **"Saldo teórico actual (efectivo)"**, con la nota del redondeo debajo (**B107**) |
| `08:12` | *"Cobros de hoy"* | lo mismo que `05:15` |

## B. Faltantes: la pantalla hace algo que el manual no cuenta

- **Accesos rápidos del Panel** (**B234**): *Cotizador* / *Nuevo cliente* arriba, distintos por
  perfil (la cajera ve solo *Cotizador*). Sin mención.
- **Tarjetas que navegan** (**B235**): 12 tarjetas del Panel son enlace; *"Cobros de hoy"*, a
  propósito, **no**. Sin mención.
- **La tarjeta del usuario como menú** (**B240**, y antes **B111**): *"Cambiar contraseña"* y
  *"Cerrar sesión"* **no aparecen en ninguno de los 8 archivos**. Es lo primero y lo último que
  hace cualquier usuario, y nunca estuvo escrito: hoy es la tarjeta al pie del menú lateral, que
  al hacerle clic abre el menú hacia arriba.
- **Préstamos** (`/prestamos`, bajo el menú *Informes*): **B241** (20 filas por página,
  *"Página 1 de 18"*, resumen por estado **de todo lo filtrado**, total de los 347 en un renglón)
  y **B241-bis** (*Plazo* y *Estado*). **La pantalla no está documentada**: 0 menciones de
  `/prestamos`, *"Página"* o *"Mostrando"* en todo el manual.
- **Informes**: aparece solo como nombre en la lista del menú de `05`. Los 43 informes, sin
  documentar.
- **Seguridad / Parámetros**: **0 menciones**. Ahí está lo de **B220/B220-bis** (píldoras de
  grupo, buscador, *"Actual"*, filas que despliegan su formulario, paginación).
- A releer al escribir, **no verificado** en este informe: el párrafo *"Atención — los números de
  este panel no avisan de problemas de caja"* de `05`, contra **B231** y **B237**.

**De B160 (los montos a la derecha) y del retiro de `/api_capital` (B158) no hay nada que
corregir**: el manual no habla de alineación de cifras y `api_capital` aparece 0 veces. B160 solo
afectaría capturas, si existieran.

## C. Las capturas: qué puede sacar Playwright y qué tiene que sacar Joel

- **Playwright puede**, y ya está autorizado desde el 05/11 (B86): 1280x800, entrando **sin que
  la contraseña se escriba en ningún lado**, captura de **viewport y nunca `fullPage`** (la barra
  lateral `fixed` de AdminLTE arruina el `fullPage`). Sirve para todo lo que solo se mira: el
  Panel de cada perfil, el menú de la tarjeta abierto, Clientes, Préstamos paginado, Informes,
  Seguridad/Parámetros.
- **Las tiene que sacar Joel** cuando la captura exige un **estado del circuito real**: caja
  abierta con movimientos, un cobro confirmado, recibo o factura, cierre con diferencia, mora con
  promesa. Armar ese estado con Playwright sería armar un escenario para la foto (**DOGMA 1** y
  D-94). En cualquier caso, **la captura registra, no prueba**.
- **Antes de sacar ninguna hay que decidir qué es el "manual visual"** (¿`.docx` con capturas?
  ¿los mismos `.md` con imágenes?). Son ~40 capturas que dependen de esa decisión.

## D. Cuánto trabajo es, y en qué orden

| Tramo | Trabajo | Cuándo |
|---|---|---|
| **A** — los 5 textos que mienten | ~1 hora, riesgo cero | antes de la defensa |
| **Panel y tarjeta del usuario bien escritos** (`05` + `08:10`) | ~media jornada | antes de la defensa |
| **Préstamos, Informes, Seguridad/Parámetros** | es **manual nuevo**: 3 archivos (`09`, `10`, `11`), 1,5–2 jornadas, recorriendo las pantallas con Joel mientras se escribe | después |
| **Manual visual con capturas** | ~1 jornada, más la decisión de formato | después |

**Dato que falta, y lo decide Joel**: si el manual de usuario es entregable de la tesis. No se
verificó porque `docs/RF_ANTEPROYECTO.md` está bajo `deny` — si hay un RF/RNF que lo pide, el
tramo de los tres módulos nuevos cambia de prioridad.
