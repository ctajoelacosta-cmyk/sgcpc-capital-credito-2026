Las capturas de pantalla las agrega Joel en el manual visual.

# Manual de usuario — Formalización

## Dónde está

Menú **"Formalización"** → opción **"Formalización"** (es la única opción de ese menú; se abre
el módulo y adentro hay un solo enlace, también llamado "Formalización"). Lleva a `/formalizacion`.

**Importante**: el sistema no le avisa en su Panel cuando el gerente aprueba una solicitud — no
hay ninguna tarjeta ni aviso para eso. Tiene que acordarse de entrar a Formalización por su
cuenta, o revisar ahí de tanto en tanto, para no dejar esperando a un cliente cuya solicitud ya
fue aprobada.

## La pantalla

Título **"Formalización"**, con la descripción **"Solicitudes aprobadas, listas para formalizar
(generar contrato, pagaré y plan de pagos)."** Un buscador —**"N.º de solicitud/operación,
cédula o nombre del cliente"**, con **"Limpiar"** y **"Buscar"**— filtra las 3 secciones de
abajo a la vez.

### Primera sección — solicitudes para formalizar

Una tabla (sin título propio) con **Código**, **Aprobada** (la fecha en que el gerente la
aprobó), **Cliente**, **Monto aprobado**, **Plazo**, y el botón **"Formalizar"**. Acá aparece
toda solicitud que el gerente ya aprobó y que todavía nadie formalizó — tóquela para generar el
contrato, el pagaré y el plan de pagos.

**Atención — esta tabla no le dice cuánto tiempo lleva esperando una solicitud.** A diferencia
de la bandeja del gerente (Aprobación), que sí tiene una columna "Esperando", acá solo ve la
fecha en que se aprobó — tiene que calcular usted mismo cuánto tiempo pasó. Revise esta pantalla
seguido para no dejar pasar mucho tiempo entre la aprobación y la formalización.

### Segunda sección — "Préstamos pendientes de desembolso"

*"Ya formalizados; el checklist de firmados debe estar completo antes de poder desembolsar."*
Columnas: **N.º de operación**, **Cliente**, **Monto**, **Formalizado** (la fecha), y
**Checklist** (**"Completo"** en verde o **"Incompleto"** en gris), con el botón **"Ver"**. Acá
caen los préstamos una vez que usted ya generó los 3 documentos — quedan aquí hasta que se
carguen los 3 firmados (contrato, pagaré y plan de pagos) y la cajera pueda desembolsar.

### Tercera sección — "Préstamos finalizados — cierre documental pendiente"

Columnas: **N.º de operación**, **Cliente**, **Monto**, con el botón **"Cierre documental"**.
Acá aparece un préstamo recién cuando ya está **totalmente pagado** (ninguna cuota pendiente) —
no tiene nada que ver con el desembolso ni con los documentos firmados de la segunda sección.
Es un trámite aparte, posterior: **devolver al cliente el pagaré firmado** (y, si corresponde,
dejar una constancia de esa devolución). Mientras un préstamo sigue activo (con cuotas por
cobrar), nunca va a aparecer acá.

## Formalizar una solicitud

Al tocar **"Formalizar"** sobre una fila de la primera sección, llega a
`/formalizacion/nueva/{id}`, con el título **"Formalizar [código]"**. Dos tarjetas:

- **"Condiciones aprobadas (no editables)"**: cliente y C.I., monto aprobado (con el número y,
  debajo, en letras), plazo, TEA/TNA, cuota más alta, total a pagar, CFT, y la afectación —
  pero **recalculada en este mismo momento con los datos actuales del cliente**, no la que
  quedó congelada cuando el gerente aprobó. Si el cliente dejó de presentar comprobante de
  ingreso, aparecería una fila roja avisándolo.
- **"Generar documentos"**: un aviso le recuerda que la fecha aprobada en la solicitud puede
  haber quedado desactualizada si formaliza varios días después. El campo **"Primer
  vencimiento"** le propone **un mes desde hoy** (no desde la fecha de la solicitud), con el
  rango permitido debajo (por ejemplo, "Entre 12/11/2026 y 07/12/2026."). **Si la fecha que
  elige cae sábado, domingo o feriado, esta pantalla sí le avisa en el momento** y le muestra
  en qué día hábil quedaría de verdad el primer vencimiento — es la única pantalla del sistema
  que hace este aviso (ni el Cotizador ni "Nueva solicitud" lo hacen, aunque a veces propongan
  una fecha que cae en un día no hábil sin decírselo).

Botones: **"Generar documentos"** y **"← Volver a la bandeja"**.

## Cierre documental, paso a paso

Cuando un préstamo de la tercera sección ya figura "totalmente pagado", toque **"Cierre
documental"**. La pantalla lo guía en 3 pasos, uno a la vez:

1. **"Generar constancia de devolución"**: genera un PDF propio (numerado `DEV-aaaa-nnnnnn`)
   con la instrucción de imprimirlo, hacerlo firmar por el cliente al entregarle el pagaré
   original, y cargar después el escaneo firmado. No le pide ninguna confirmación antes de
   generarlo.
2. **Cargar el escaneo firmado**: recién aparece después del paso 1 — suba la foto o el escaneo
   del PDF ya firmado por el cliente.
3. **"Marcar cierre documental completo"**: recién aparece después del paso 2, con una
   confirmación que le avisa que **no se puede deshacer**. Al confirmar, el préstamo queda con
   el cierre documental **COMPLETO**, con la fecha y quién lo completó.

**Atención — mientras no complete este trámite, el sistema sigue dejando la puerta abierta a
una reversión del cobro que canceló el préstamo** (con la misma casilla de la factura de
siempre). Recién cuando el cierre documental queda COMPLETO, esa opción desaparece sola, y en
su lugar aparece "No admite reversión: pagaré devuelto al cliente" — no tiene que acordarse de
nada para que deje de ofrecerse, pero tampoco demore este trámite sin necesidad.

**Atención — su Panel no le avisa cuando hay un préstamo esperando este trámite.** Tiene que
acordarse de revisar la tercera sección de esta pantalla por su cuenta, igual que ya tiene que
acordarse de revisar la primera sección (solicitudes para formalizar).

## Lo que se vio el 23/10/2026 (segundo cliente de práctica)

Con el segundo cliente de práctica, la primera sección tenía una sola fila
(su solicitud, aprobada el día anterior); las secciones 2 y 3 estaban **vacías** — el préstamo
del primer cliente de práctica ya había avanzado más allá de "pendiente de desembolso" (ya
estaba desembolsado y activo), y todavía no hay ningún préstamo totalmente pagado en el
sistema. Al formalizar, la fecha estimada que traía la solicitud (22/11/2026, un domingo) ni
siquiera se usó: la pantalla propuso directamente 23/11/2026 (un mes desde la fecha real de
formalización) — que cayó lunes, así que no hubo ningún aviso de día no hábil que mostrar.

## Lo que se vio el 25/10/2026 (cierre documental, primera vez en el proyecto)

Con `CC-2026-00002` ya cancelado en su totalidad por la cajera, apareció en la tercera sección.
"Generar constancia de devolución" dio `DEV-2026-000001`; el PDF citó el pagaré y la constancia
de cancelación `CAN-2026-000001`, con las firmas del representante legal y del cliente
("Recibí el pagaré original"). Se cargó un escaneo firmado (de prueba) y, al tocar "Marcar
cierre documental completo" con su confirmación de "no se puede deshacer", la franja verde
mostró "COMPLETO — 25/10/2026 11:37 por [oficial]" y las cards 2 y 3 desaparecieron. El Panel
del oficial no avisó nada de esto en ningún momento — tuvo que entrar a Formalización por su
cuenta a revisar la tercera sección.
