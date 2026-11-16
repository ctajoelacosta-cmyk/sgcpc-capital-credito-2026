Las capturas de pantalla las agrega Joel en el manual visual.

# Manual de usuario — Nueva solicitud

## Dónde está

Desde la ficha del cliente, pestaña **④ Solicitud** (o el botón **"Siguiente: ④ Solicitud →"**,
que aparece cuando ya completó el Expediente y la Consulta al buró — es el mismo botón, solo
cambia de color y de texto). Al tocarlo, **sale de la ficha del cliente** y pasa a otro módulo,
"Solicitudes" (así lo va a ver marcado en el menú de la izquierda).

## La pantalla "Nueva solicitud"

Un aviso le recuerda: *"Los campos marcados con \* son obligatorios."* Complete:

- **Cédula del cliente**: ya viene precargada (si llegó desde la ficha del cliente), con la
  ayuda *"El cliente debe estar registrado en 'Clientes' antes de crear la solicitud."*
- **Monto (Gs.)**.
- **Plazo (meses)**.
- **Destino del crédito**: escriba libremente para qué es el préstamo (no hay una lista
  predefinida).
- **Fecha estimada del primer vencimiento**: la pantalla le propone un mes desde hoy. Es una
  fecha **estimada** — la definitiva se fija al formalizar, más adelante.

A medida que completa monto y plazo, aparece debajo, sin recargar la página: **"Cuota estimada:
[monto] Gs. · Afectación: [x,x] % (límite: 40 %)."** — si la afectación supera el límite, se
muestra en rojo con "Baje el monto o alargue el plazo." al final. Es solo una vista previa; no
bloquea nada todavía.

Botones: **"Crear solicitud"** y **"Cancelar"**.

**Importante — el sistema no le va a avisar acá si al cliente le falta algo del Expediente**
(por ejemplo, si todavía no cargó la cédula o el comprobante de ingreso). Puede crear la
solicitud igual. El aviso de qué falta recién aparece más adelante, al **enviar la solicitud a
aprobación** — así que, antes de crear la solicitud, es buena práctica volver a la ficha del
cliente y revisar que el Expediente (②) y la Consulta al buró (③) estén completos.

## Qué pasa al crear

La solicitud se crea directo, **sin ningún cuadro de aviso**, en estado **Borrador**, y lo lleva
derecho a la ficha de esa solicitud nueva (`SOL-[año]-[número]`).

## La ficha de la solicitud (en Borrador)

- Título: **"Solicitud SOL-[año]-[número]"**, con la etiqueta **"Borrador"**.
- Tarjeta **"Cliente"**: nombre y C.I., el ingreso neto comprobado (o "declarado, sin
  comprobante" si no presenta), "Presenta comprobante de ingresos: Sí/No", y un enlace **"Ver
  ficha completa / documentos"** para volver a la ficha del cliente si necesita revisar algo.
- Tarjeta **"Condiciones y simulación"**: monto, plazo, destino y primer vencimiento estimado,
  todos editables mientras siga en Borrador — con el mismo indicador de cuota/afectación en
  vivo de arriba. Botón **"Simular"**, con la ayuda *"Guarda las condiciones y recalcula la
  cuota y la capacidad de pago."* (no es solo una vista previa — guarda de verdad, puede
  tocarlo cuantas veces necesite).
- Debajo, los resultados de la simulación: **cuota más alta (con IVA)**, **TEA / TNA**, **CFT
  simulado**, **total a pagar (con IVA)** y **afectación al ingreso** (con el límite al lado).
- **Lista de 8 requisitos**, cada uno con ✓ (verde) o ✗ (rojo): edad dentro de la política,
  zona habilitada, situación laboral admitida, cédula vigente cargada en el expediente,
  comprobante de ingresos cargado, autorización de consulta al buró vigente, consulta al buró
  con evidencia registrada, y sin deudas en Informconf.
- Un aviso: **"Cumple validaciones preliminares"** (verde) o **"No cumple validaciones
  preliminares"** (amarillo) según si los 8 están en ✓.
- Tres botones: **"Enviar a aprobación"** (verde) y **"Cancelar solicitud"** (rojo) juntos, y
  aparte, **"Volver a la lista"**.

**Atención — "Enviar a aprobación" se ve habilitado aunque algún requisito esté en ✗.** No es
un error: si lo toca con algo pendiente, el sistema lo va a rechazar y le va a mostrar qué le
falta completar. Pero conviene revisar la lista de 8 requisitos **antes** de tocarlo, para no
toparse con el rechazo después del clic.

### Si le aparece "Faltan completar estos puntos antes de enviar a aprobación"

Si toca "Enviar a aprobación" con algún requisito en ✗, no le va a pedir confirmación antes —
va a aparecer directo un cuadro rojo con el título **"Faltan completar estos puntos antes de
enviar a aprobación:"** y la lista exacta de lo que falta (por ejemplo: *"Falta cargar la
cédula de identidad vigente del cliente en su expediente."*, *"El cliente indica que presenta
comprobante de ingresos, pero no hay ninguno cargado en su expediente."*), con un botón
**"Entendido"**.

**No pasó nada malo**: la solicitud sigue en Borrador, tal cual estaba, y no se envió nada al
gerente. Simplemente:

1. Lea la lista del cuadro — le dice exactamente qué le falta.
2. Toque **"Entendido"** — se queda en la misma solicitud, no lo lleva a ningún lado.
3. **Si faltan documentos**: use el enlace **"Ver ficha completa / documentos"** de la tarjeta
   "Cliente" (arriba de todo, en esta misma ficha de la solicitud) — es más corto que ir por
   el menú "Clientes" y buscarlo de nuevo. Lo va a llevar a la pestaña **① Datos** de la ficha
   del cliente; desde ahí, toque la pestaña **② Expediente** y cargue lo que diga la lista.
4. Vuelva a la solicitud (desde "Solicitudes y préstamos", en la ficha del cliente) y toque
   "Enviar a aprobación" de nuevo.

**El camino de vuelta es más corto que el de ida.** Una vez que la solicitud ya existe, volver
a ella desde la ficha del cliente es **un solo clic** — la pestaña **④** (que ya muestra el
número y el estado de la solicitud, por ejemplo "SOL-2026-0002 (Borrador) →") lo lleva directo.
Es más rápido que el camino inverso (de la solicitud al Expediente), que hoy le puede tomar
varios pasos — tenga presente esta diferencia mientras no se pareje en una próxima mejora.

### Cuando los 8 requisitos ya están en ✓

Con el Expediente y el Buró completos, al volver a la solicitud va a ver los 8 requisitos en
✓, y el aviso cambia a **verde: "Cumple validaciones preliminares"**. La solicitud sigue en
Borrador — entrar a mirarla no la envía ni la cambia de estado. Ahora sí, al tocar **"Enviar a
aprobación"**, el sistema le va a pedir confirmación: **"¿Enviar esta solicitud a aprobación?"**,
con el botón **"Sí, enviar"** — a diferencia de antes (con algún requisito en ✗), donde el
envío iba directo sin preguntarle nada.

**Atención — "Cancelar solicitud" (rojo) está justo al lado de "Enviar a aprobación" (verde).**
Al tocarlo, se abre un formulario aparte (no cancela de un solo clic): pídale un motivo de la
lista y confirme. La confirmación le va a decir **"¿Cancelar esta solicitud? No se puede
deshacer."** — tómeselo en serio: una vez cancelada, no hay forma de reactivarla ni de volverla
a Borrador. Si se equivocó de botón, no confirme — la solicitud sigue intacta hasta que usted
dice que sí.

## Qué pasa al enviarla a aprobación

Con los 8 requisitos en ✓ y confirmando **"Sí, enviar"**, va a ver un cuadro de éxito:
**"Solicitud enviada a aprobación. Ahora la revisa el gerente en Aprobación."**, con
**"Aceptar"**. La pantalla vuelve a la misma ficha de la solicitud, pero ahora distinta:

- El título ya no dice "Borrador": dice **"Pendiente de aprobación"**.
- Un aviso le confirma: **"Enviada por [su nombre] el [fecha y hora]. Espera la decisión del
  gerente."**
- La tarjeta "Cliente" ahora dice **"Ingreso neto comprobado (evaluado al enviar)"** — es un
  dato importante: ese número **queda congelado** en el momento del envío. Aunque después
  cambie algo del cliente, esta solicitud sigue mostrando el ingreso de ese momento.
- "Condiciones y simulación" pasa a **solo lectura**: sigue viendo la cuota, TEA/TNA, CFT,
  total a pagar y afectación, pero **ya no hay campos para editar ni botón "Simular"**.
- **Desaparecen la lista de 8 requisitos y los botones "Enviar a aprobación"/"Cancelar
  solicitud"**. Solo le queda **"Volver a la lista"**.

**¿Qué significa "Pendiente de aprobación"?** Que la solicitud ya salió de sus manos: usted, el
oficial, **ya no puede modificarla ni retirarla** desde esta pantalla — ni el monto, ni el
plazo, ni nada. Queda esperando a que el gerente la revise en su propia bandeja de Aprobación
(no le llega ningún aviso aparte; el gerente la ve ahí cuando entra a su Panel). Si el cliente
se arrepiente o hay algún error y la solicitud necesita cerrarse antes de que el gerente decida,
**tiene que pedírselo al gerente** — solo él puede cancelarla en ese estado, desde la pantalla
de Aprobación.

## Volver a la ficha del cliente

Al entrar a "Nueva solicitud" desde la ficha del cliente, sale completamente de esa ficha — no
hay un enlace directo de vuelta, salvo "Ver ficha completa / documentos" dentro de la ficha de
la solicitud ya creada. Si necesita revisar algo del cliente antes de eso (Expediente, Buró,
Datos), use el menú **"Clientes y solicitudes" → "Clientes"** y búsquelo de nuevo por cédula o
nombre.
