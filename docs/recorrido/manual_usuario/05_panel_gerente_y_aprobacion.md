Las capturas de pantalla las agrega Joel en el manual visual.

# Manual de usuario — Panel del gerente y Aprobación

## El Panel

Es la primera pantalla que ve el gerente después de iniciar sesión (`/panel`). Arriba de todo,
un saludo según la hora: **"Buenos días"**, **"Buenas tardes"** o **"Buenas noches"**, seguido
de su nombre — y debajo, **"Este es el resumen de hoy."**

Los indicadores están agrupados en 3 bloques:

### "Hoy"

- **Cobros de hoy**: cuántos pagos se confirmaron hoy, con el monto total cobrado entre
  paréntesis.
- **Promesas de hoy**: cuántas promesas de pago (de Seguimiento de mora) vencen hoy, con el
  enlace **"Ir a Seguimiento de mora"**.
- **Efectivo en caja**: el saldo de la caja abierta en este momento, o **"Sin caja abierta"** si
  todavía no se abrió ninguna hoy — con el enlace **"Ir a Caja"**.
- **Solicitudes pendientes de aprobar**: cuántas solicitudes están esperando su decisión, con el
  enlace **"Ir a Aprobaciones"**. Es la forma en que usted se entera de que llegó una solicitud
  nueva — no hay ningún aviso aparte (ni correo, ni notificación emergente): tiene que mirar el
  panel o entrar directamente a Aprobación.

### "Cartera"

- **Préstamos en mora**: cuántos préstamos tienen alguna cuota vencida sin cobrar hoy.
- **Cartera en la calle (capital pendiente)**: la suma del capital que todavía deben todos los
  préstamos activos — no es el monto que se prestó originalmente, es lo que falta cobrar hoy.
- **Cobrado esta semana**: lo cobrado desde el lunes de esta semana hasta hoy.
- **Cobrado este mes**: lo cobrado desde el día 1 de este mes hasta hoy.

### "Pendientes"

- **Préstamos pendientes de desembolso**: cuántos préstamos ya aprobados y formalizados están
  esperando que la cajera los desembolse, y cuántos de ellos ya están **listos** (con el
  checklist de documentos completo) — con el monto total a entregar y el enlace **"Ir a
  Desembolsos"**. Si a alguno le falta documentación, la tarjeta se lo dice con nombre y
  apellido: **"falta cargar: Contrato firmado, Pagaré firmado"**, y le ofrece el enlace **"Ir a
  Formalización"** — que es la pantalla donde se cargan esos papeles. Esa carga **le toca a usted
  o a la oficial**: la cajera no tiene permiso para subir documentos. Mientras falte algo, el
  préstamo cuenta como "pendiente" pero no como "listo".
- **Reversiones por autorizar**: cuántas solicitudes de reversión de un cobro están esperando su
  autorización, con el enlace **"Ir a Reversiones"**. En esa bandeja, una solicitud puede venir
  marcada en amarillo con **"Ya hay una operación posterior: solo se puede rechazar."**: quiere
  decir que, después de ese cobro, se confirmó otra operación sobre el mismo préstamo (otro
  cobro, un adelanto o una cancelación), y el sistema ya no puede deshacer el cobro de abajo sin
  romper el de arriba. El botón "Autorizar" sigue a la vista, pero va a fallar: lo único que
  corresponde ahí es **rechazar**, explicando el motivo. Si el cobro igual hay que corregirlo,
  primero hay que revertir las operaciones posteriores, en orden, de la más nueva a la más
  vieja.

**Atención — los números de este panel no avisan de problemas de caja.** Si ve "Efectivo en
caja: 0 Gs." y, al mismo tiempo, alguna solicitud pendiente de aprobar, tenga en cuenta que, si
la aprueba, más adelante la caja va a necesitar ese dinero para poder desembolsarla — el panel,
hoy, no se lo recuerda en ningún lado.

**Menú del gerente**: Panel, Clientes y solicitudes, Aprobación, Formalización, Desembolso y
caja, Cobranza y mora, Cancelación, Informes, Seguridad.

## Aprobación

### La bandeja (`/aprobaciones`)

Título **"Aprobaciones"**, con la descripción **"Solicitudes pendientes de aprobación, en
orden de llegada."** Dos pestañas: **"Pendientes"** e **"Historial"**.

**Pendientes** lista las solicitudes que esperan su decisión, **las más antiguas primero** (es
el orden en que de verdad llegaron a aprobación). Columnas: **Código**, **Enviada** (la fecha),
**Esperando** (cuánto tiempo pasó desde que se envió — por ejemplo "51 min", "2 h 10 min" o "1
día 3 h"; se cuenta siempre desde el envío a aprobación, no desde que el cliente llegó a la
sucursal, y **no cambia de color ni avisa nada aunque pase mucho tiempo**), **Cliente** (nombre
y C.I.), **Monto solicitado**, **Plazo**, **Afectación**, y el botón **"Revisar"**. Puede
buscar por **N.º de solicitud, cédula o nombre del cliente**, con **"Limpiar"** y **"Buscar"**.

**Historial** muestra las solicitudes ya decididas (**Aprobada**, **Rechazada** o
**Cancelada**), **las más recientes primero**. No tiene la columna "Esperando" — en su lugar
muestra **Decisión**, **Decidida por** y **Fecha de decisión**; el botón dice **"Ver"** en vez
de "Revisar". Esta pestaña tiene filtros propios: un **Estado** (Todas/Aprobada/Rechazada/
Cancelada) y un rango de fechas **desde/hasta** (sobre la fecha en que se decidió), además de
un enlace para exportar.

**Atención — la bandeja no muestra el resultado del buró.** Para ver si una solicitud tiene
mora o qué clasificación le dio Informconf, tiene que tocar "Revisar" y entrar a la ficha — la
lista, hoy, no distingue de un vistazo cuál es más riesgosa que otra.

### Revisar una solicitud, antes de decidir (`/aprobaciones/{id}`)

Al tocar "Revisar" en la bandeja, llega a la ficha de la solicitud, con el título **"Solicitud
SOL-[año]-[número]"** y la etiqueta **"Pendiente de aprobación"**. Tres tarjetas:

- **"Cliente"**: nombre y C.I., **"Ingreso neto comprobado (evaluado al enviar)"**, el enlace
  **"Ver ficha / expediente"** (si necesita repasar algo del cliente), una línea con **Edad**,
  **Zona**, **Situación laboral**, **Empleador** (con la antigüedad) y **Cuota de otras
  deudas**, y debajo el mismo **checklist de 8 requisitos** que ya vio el oficial, recalculado
  en este mismo momento (no una foto vieja de cuando se envió).
- **"Condiciones solicitadas y simulación"**: monto (con el número y, debajo, en letras),
  plazo, destino, primer vencimiento estimado, cuota más alta (con IVA), TEA/TNA, CFT, total a
  pagar, y la **afectación al ingreso**, resaltada en **verde** si está dentro del límite o en
  **amarillo** si lo supera. **Todo de solo lectura** — no hay ninguna forma de cambiar el
  monto, el plazo ni ninguna otra condición desde esta pantalla: usted solo puede aprobar
  exactamente lo que se solicitó, o rechazarlo/cancelarlo. **Esto es así por diseño, no es una
  limitación de la pantalla**: si necesita otras condiciones, rechace o cancele esta solicitud
  y que el oficial cree una nueva con el monto/plazo que corresponda — así queda trazabilidad
  clara de qué se pidió y qué se decidió en cada caso, en vez de una misma solicitud cambiando
  de condiciones a mitad de camino.
- **"Resultado del buró (Informconf)"**: Persona, Fecha, Puntaje, Clasificación, ¿Mora?,
  Resumen, y "Ver" para la evidencia cargada.

Al pie: los botones **"Aprobar"** (verde), **"Rechazar"** (rojo) y **"Cancelar solicitud"**
(con el borde en rojo, sin relleno — para que no se confunda con "Rechazar" de un vistazo), y
**"← Volver a la bandeja"**.

### Aprobar

Al tocar **"Aprobar"**, confirme **"¿Aprobar esta solicitud?"** con **"Sí, aprobar"** (el botón
alternativo del diálogo dice "Cancelar" — no cierra nada del crédito, solo descarta el
diálogo). El sistema vuelve a revisar, en ese mismo instante, que nada haya cambiado desde el
envío: tope de tasa vigente, afectación dentro del límite, y que el cliente siga sin mora en
Informconf — si aparece una mora nueva, el sistema bloquea la aprobación, sin excepción. Al
aprobar, se otorga **exactamente** lo solicitado (nunca otro monto, plazo o tasa).

Aparece el mensaje **"Solicitud aprobada. Ahora se formaliza en Formalización."** — en la
práctica, quien formaliza normalmente es **la oficial** (no usted); este aviso no lo aclara.
Con "Aceptar", vuelve a la misma ficha, ahora así:

- Estado **"Aprobada"**, con la línea **"Aprobada por [usuario] el [fecha y hora]. Monto
  aprobado: [monto], plazo: [n] meses."**
- Debajo, **el paso siguiente, con su botón** — así no hace falta buscar Formalización en el
  menú:
  - si todavía nadie la formalizó: **"Ahora se formaliza: se generan el contrato, el pagaré y el
    plan de pagos."** + **"Formalizar esta solicitud →"**;
  - si ya se formalizó: **"Ya formalizada como [CC-…]."** + **"Ver el préstamo [CC-…] →"**, que
    abre la ficha de ese préstamo en Formalización (donde se cargan los firmados y se ve si está
    listo para el desembolso).

  Ese botón **no cambia quién puede hacer qué**: si el sistema no le da acceso a Formalización,
  tampoco se lo va a dar por entrar desde acá.
- **Desaparecen** "Aprobar", "Rechazar" y la lista de los 8 requisitos.
- **Siguen** "Cancelar solicitud" y "Volver a la bandeja" — la pantalla no vuelve sola a la
  bandeja, tiene que tocar ese botón.

**Atención — "Cancelar solicitud" sigue ahí, con la solicitud ya Aprobada.** Es el camino
correcto mientras la oficial todavía no formalizó (ver más abajo). Una vez que ya se formalizó
(ya existe un préstamo), la vía pensada para dar de baja la operación es **"Anular préstamo"**,
desde Formalización — evite usar "Cancelar solicitud" sobre una solicitud que ya sabe
formalizada, aunque el botón siga visible; podría dejar la solicitud marcada "Cancelada" sin
tocar el préstamo que ya existe.

### Rechazar

Pide un motivo de una lista fija (Informconf con deudas, capacidad de pago insuficiente,
documentación incompleta o inconsistente, u otro con detalle). Una vez rechazada, queda así para
siempre — no se puede reactivar. El cliente puede empezar una solicitud nueva más adelante si
corresponde.

### "Cancelar solicitud"

**Atención — no confunda "Cancelar solicitud" con "Rechazar".** Rechazar es una decisión suya
sobre el crédito (no corresponde otorgarlo). Cancelar es cortar el trámite sin juicio sobre el
crédito — por ejemplo, si el cliente desiste o hubo un error de carga. Pide un motivo
obligatorio y confirma con **"¿Cancelar esta solicitud? No se puede deshacer."** Es el único
camino, hoy, para cerrar una solicitud que el oficial ya envió — el oficial, una vez enviada, no
tiene ningún botón propio para retirarla; si el cliente desiste después del envío, el oficial
tiene que avisarle a usted para que la cancele desde acá.
