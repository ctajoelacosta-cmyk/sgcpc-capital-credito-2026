Las capturas de pantalla las agrega Joel en el manual visual.

# Manual de usuario — Cotizador

## Para qué sirve

El Cotizador le responde a usted (o al cliente, mientras lo atiende) la pregunta **"¿cuánto
pagaría si pide este préstamo?"**. Es una simulación: no guarda nada en el sistema ni compromete
a la financiera ni al cliente — puede probar todas las combinaciones de monto y plazo que haga
falta, sin ningún riesgo.

Lo pueden usar los 4 perfiles: el oficial de crédito (es su herramienta principal, antes de
registrar a un cliente nuevo), el gerente y la cajera (para resolver una consulta rápida) y el
administrador.

## Dónde está

En el menú de la izquierda: **"Clientes y solicitudes" → "Cotizador"**.

## El formulario

Complete estos datos:

- **Monto**: entre 500.000 y 15.000.000 Gs. A medida que escribe, el sistema le muestra el monto
  en letras debajo del campo, para que verifique que no se equivocó de cifra.
- **Plazo**: entre 3 y 24 meses.
- **Fecha estimada del primer vencimiento**: dentro del rango permitido que le muestra la
  pantalla. Es una fecha **estimada** — la definitiva se fija recién cuando el préstamo se
  formaliza, más adelante en el circuito. **Consejo**: ponga una fecha de aproximadamente un mes
  desde hoy. La primera cuota se cobra siempre como un mes completo, sin importar los días
  reales que falten hasta esa fecha (así quedó decidido, ver D-156) — si pone una fecha muy
  lejana, la cotización igual le va a mostrar una cuota de "un mes", y eso puede confundir al
  cliente si todavía falta mucho más que un mes para esa fecha.
- **Nombre y C.I.** del interesado: opcionales. Solo se usan si después imprime el PDF para
  entregárselo al cliente — no quedan guardados en ningún lado.

Botones: **"Limpiar"** (vacía el formulario) y **"Cotizar"** (calcula el resultado).

## El resultado

Lo primero que va a ver es un aviso: **"Cotización informativa, no constituye aprobación del
crédito."** Recuérdeselo siempre al cliente — todavía falta que presente sus documentos y que el
gerente apruebe la solicitud.

Después, estos datos:

- **Monto solicitado**: el monto que usted puso.
- **Cuota sin IVA**: capital más interés, siempre el mismo valor en todas las cuotas (es el
  sistema francés de amortización).
- **Cuota con IVA (1.ª cuota)**: la que el cliente paga de verdad. Baja levemente mes a mes,
  porque el IVA se cobra sobre el interés, y el interés baja a medida que se devuelve capital.
- **Total a pagar**: la suma de todas las cuotas del plan, con IVA.
- **TEA** (Tasa Efectiva Anual): el interés real que se paga en un año, porque se calcula mes a
  mes sobre lo que todavía se debe. Es la tasa que controla la ley: no puede superar el tope
  vigente del Banco Central.
- **TNA** (Tasa Nominal Anual): la tasa de cada mes multiplicada por 12. Es menor que la TEA
  porque no suma el efecto mes a mes.
- **CFT** (Costo Financiero Total): todo lo que cuesta el préstamo en un año, intereses más IVA,
  sin comisiones ni gastos (la financiera no cobra ninguno). Es el número que conviene usar para
  comparar con otra financiera.

Junto a cada uno de estos términos hay un ícono ⓘ: tóquelo y le muestra la explicación completa,
sin tener que salir de la pantalla ni preguntarle a nadie.

## Comparar plazos

Una tabla chica le muestra, para el mismo monto, cómo cambiaría la cuota y el total a pagar con
6, 12, 18 y 24 meses de plazo. Regla simple para explicarle al cliente: **a más meses, la cuota
es menor, pero el total a pagar es mayor** (se pagan más meses de interés). Es útil cuando la
cuota le queda alta al cliente con el plazo que pidió al principio — puede mostrarle, ahí mismo,
qué pasaría con un plazo más largo.

## Plan de pagos

La tabla completa, cuota por cuota, con estas columnas:

- **Vencimiento**: la fecha de cada cuota. Si cae sábado, domingo o feriado, se corre al
  siguiente día hábil.
- **Capital**: lo que se devuelve del préstamo en esa cuota — sube un poco cada mes.
- **Interés**: lo que cobra la financiera por prestarle — baja un poco cada mes (porque ya se
  devolvió parte del capital).
- **IVA**: el 10 % del interés de esa cuota — por eso baja junto con el interés.
- **Total**: capital + interés + IVA — es lo que el cliente paga ese mes.
- **Saldo**: lo que todavía queda por devolver después de esa cuota — en la última fila, llega a
  0.

## Botones finales

- **"Nueva cotización"**: limpia todo y empieza de nuevo.
- **"Imprimir"**: genera un PDF con el mismo resultado, para entregárselo al cliente en mano.

## Qué sigue

Si al cliente le interesa avanzar con el préstamo, el siguiente paso es la **precalificación**
— ahí se revisa si el cliente, con sus datos reales, califica para el crédito.
