Las capturas de pantalla las agrega Joel en el manual visual.

# Manual de usuario — Clientes y precalificación

## Pantalla Clientes

**Para qué sirve**: es la lista de todas las personas ya registradas en el sistema, y el punto
de partida para registrar a una persona nueva.

- Botón **"+ Nuevo cliente"**: abre el formulario de alta.
- **Filtros**: cédula o nombre; estado (Activos/Inactivos/Todos); alta desde / alta hasta;
  préstamo activo (Sí/No/Todos); departamento; ciudad (se filtra sola según el departamento
  elegido); situación laboral. Botones **"Limpiar"** y **"Buscar"**.
- **Resultado**: tabla con C.I., Apellidos y nombres, Teléfono, Ciudad, Situación laboral,
  Préstamos activos y Estado, más un botón **"Ver / editar"** (o solo **"Ver"** si su perfil
  no puede editar) por fila.
- Botones **"PDF"** y **"Excel/CSV"**: exportan la lista con los mismos filtros que tenga
  puestos en ese momento.

**Buena práctica**: antes de dar de alta a alguien, busque siempre primero por su número de
cédula. Si ya existe, el sistema no le va a dejar registrarlo de nuevo con la misma cédula — pero
buscar primero le ahorra llenar todo el formulario para encontrarse con ese rechazo al final.

## Nuevo cliente

Al tocar "+ Nuevo cliente" se abre un formulario con pestañas en la parte de arriba: **① Datos**,
② Expediente, ③ Consulta al buró, ④ Solicitud. Mientras no registre a la persona, solo puede
trabajar en **① Datos** — un aviso le recuerda: *"Expediente y Autorización se habilitan después
de registrar al cliente."*

Los campos marcados con **\*** son obligatorios — no puede guardar sin completarlos.

## Precalificación

Es la primera caja del formulario, antes de todo el resto de los datos. Son 4 preguntas:

1. **Fecha de nacimiento** (la edad permitida es de 22 a 71 años).
2. **Departamento** (hoy, Asunción o Central).
3. **Situación laboral** (solo las que tienen ingreso fijo comprobable).
4. **¿El cliente puede presentar un comprobante de su ingreso?** (Sí/No).

**¿Por qué va primero?** Para que usted sepa, en segundos, si la persona puede llegar a ser
cliente — antes de cargar todos sus datos y de pedirle los papeles. Evita el trabajo de llenar
toda la ficha para recién al final descubrir que la persona no califica.

**Si califica**: la edad calculada aparece en verde al lado de la fecha de nacimiento, y debajo
de las 4 preguntas aparece, en verde y negrita, **"Califica: puede continuar con el registro."**
El resto del formulario, que hasta ese momento estaba bloqueado, se desbloquea solo, y se habilita
el botón **"Imprimir requisitos"**.

**Si no califica**: el mismo indicador aparece en rojo, con el motivo puntual (por ejemplo: "la
edad permitida es de 22 a 71 años; esta persona tiene 18."). El resto del formulario sigue
bloqueado — no tiene sentido seguir completando los datos de alguien que, hoy, no puede ser
cliente.

Botón **"Limpiar"**: vacía la precalificación y todo el resto del formulario, y lo vuelve a dejar
bloqueado — para empezar de cero con otra persona sin salir de la pantalla.

## Imprimir requisitos

Con la situación laboral elegida, el botón **"Imprimir requisitos"** genera un PDF titulado
**"REQUISITOS PARA SOLICITAR UN PRÉSTAMO"**, con:

- Encabezado con los datos de la empresa.
- La situación laboral elegida.
- Líneas en blanco para que el cliente complete a mano su **nombre** y **C.I.**.
- **"Documentos a presentar"**, según la situación laboral. Ejemplo, para un asalariado:
  - Cédula de identidad (ambos lados).
  - Factura de ANDE/ESSAP/telefonía (comprobante de domicilio).
  - Liquidación de sueldo o extracto de aportes IPS (últimos 3 a 6 meses).
- **"Condiciones generales"**: edad admitida, zona habilitada, monto y plazo posibles, y que el
  comprobante de ingreso es obligatorio.
- Un aviso: *"Documento informativo, no constituye aprobación del crédito. Las condiciones
  finales dependen de la evaluación crediticia."*
- Pie: "Emitido por: [su nombre] — [fecha y hora]".

Entrégueselo al cliente en mano: así sabe exactamente qué traer la próxima vez que venga, sin
que usted tenga que explicárselo de memoria cada vez.

## Resto del formulario

Una vez que la precalificación califica, complete:

- **Datos personales**: cédula, vencimiento de cédula, nombres, apellidos, estado civil, etc.
- **Domicilio**: ciudad (según el departamento ya elegido en Precalificación), barrio, dirección,
  tipo de vivienda, antigüedad.
- **Contacto**: teléfono y los demás datos de contacto.
- **Trabajo e ingresos**: datos del empleador (si corresponde a la situación laboral) y los
  montos de ingreso. El dato clave es el **ingreso neto comprobado** — es el que de verdad se usa
  para calcular cuánto puede pagar el cliente. **Otros ingresos mensuales** y **gastos fijos
  mensuales** son solo informativos, no entran en ningún cálculo. La **cuota mensual de otras
  deudas** sí es importante: se resta de la capacidad de pago cuando más adelante se evalúe una
  solicitud de crédito.

  Si eligió una situación laboral con empleador (por ejemplo, asalariado del sector privado),
  aparecen además: **Empleador: nombre**, **RUC (opcional)**, **Dirección laboral**, **Teléfono
  laboral**, **Antigüedad laboral (años)** y **Cargo** — todos obligatorios para esas
  situaciones. Si la situación laboral no tiene empleador (comerciante, jubilado, etc.), estos
  campos ni se muestran.
- **Referencias**: dos personas de referencia, con nombre y teléfono.

## Registrar al cliente y su ficha

Al terminar de completar el formulario, toque **"Registrar cliente"**.

**Mensaje de éxito**: *"[Nombre y apellido] (C.I. [número]) se registró correctamente.
Siguiente paso: cargar sus documentos en Expediente."* El sistema lo lleva directo a la **ficha**
del cliente recién creado.

### La ficha del cliente

La ficha tiene pestañas en la parte de arriba, que marcan el orden real del trámite:

- **① Datos** — lo que acaba de completar (siempre con ✓, ya está).
- **② Expediente** — cargar los documentos del cliente.
- **③ Consulta al buró** — autorización y resultado de la consulta crediticia.
- **④ Solicitud** — crear la solicitud de crédito (recién se habilita con ② y ③ completos).
- **Solicitudes y préstamos** — historial de todo lo que el cliente ya tramitó (visible siempre,
  no es un paso del trámite en sí).

Dentro de ① Datos, dos botones al pie del formulario: **"Guardar cambios"** (ya no dice
"Registrar cliente": la persona ya existe) y **"Volver"** (sale sin guardar). Un tercer botón,
**"Siguiente: ② Expediente →"**, lo lleva directo a cargar los documentos — el siguiente paso de
este manual.

**Edición posterior**: si más adelante necesita corregir algún dato del cliente (por ejemplo, la
fecha de nacimiento o el domicilio), puede volver a esta misma pestaña ① Datos y usar "Guardar
cambios" las veces que haga falta.
