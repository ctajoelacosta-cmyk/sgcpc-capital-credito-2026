# Diseño de reportes E5 — Contrato, Pagaré y Plan de pagos

Propuesta de diseño, revisada y aprobada por Joel Acosta el 25/09/2026. Fuente de verdad para `contrato.jrxml`, `pagare.jrxml` y `plan_pagos.jrxml` (REGLAS_DESARROLLO.md §6, D-70).

> Nota: textos modelo basados en la práctica habitual. Antes de usarlos con clientes reales (después de la defensa), deben ser revisados por el abogado del cliente. Los datos de la empresa son ficticios y salen de `parametros_sistema`.

## 0. Reglas comunes a los tres reportes

- **Hoja A4 vertical**, márgenes 2 cm, fuente **DejaVu Sans** (embebida), cuerpo 10 pt, títulos 14 pt en negrita.
- **Ningún cálculo dentro del reporte.** Todos los importes, fechas y tasas llegan ya calculados desde Java (motor financiero único). El reporte solo muestra.
- **Importes:** guaraníes con punto de miles, sin decimales (`2.298.180 Gs.`). Los importes principales van también **en letras** con `ConversorNumerosALetras` (en mayúsculas dentro de los textos legales: `DOS MILLONES DOSCIENTOS NOVENTA Y OCHO MIL CIENTO OCHENTA GUARANÍES`).
- **Tasas:** con coma decimal y símbolo `%` (`27,00 %`). **Fechas:** `dd/mm/aaaa`. **Cédulas:** con puntos (`4.150.164`).
- **Encabezado (todas las páginas):** a la izquierda, `empresa.razon_social` en negrita y debajo `RUC empresa.ruc`, `empresa.domicilio`, `Tel. empresa.telefono · WhatsApp empresa.whatsapp`, `empresa.correo`, `Registro BCP: empresa.registro_bcp`. A la derecha, recuadro con **tipo de documento** y **N.º de operación** (`identificador_operacion`, por ejemplo `CC-2026-00001`).
- **Pie (todas las páginas):** `Emitido por: {nombre del usuario} — {dd/mm/aaaa hh:mm}` a la izquierda, `Página X de Y` a la derecha.
- **Codeudor:** todo bloque o firma de codeudor se muestra **solo si la solicitud tiene codeudor** (`printWhenExpression`). *Superado por el retiro de codeudor/garante (18/10/2026, commit `6e7da13`) — ver `docs/funcionamiento/RF12_documentos_pdf.md`. Esta sección y las referencias a codeudor de §1/§2/§4 quedan como diseño histórico, sin reescribir.*
- Los PDF generados se guardan en PostgreSQL. Las reimpresiones devuelven el PDF guardado, no uno nuevo.

---

## 1. CONTRATO DE PRÉSTAMO DE DINERO (`contrato.jrxml`)

**Título:** `CONTRATO DE PRÉSTAMO DE DINERO` — `N.º {identificador_operacion}`

**Texto (los campos entre llaves se reemplazan por datos):**

En la ciudad de Asunción, República del Paraguay, a los {fecha de formalización en letras: "veinticinco días del mes de septiembre de dos mil veintiséis"}, entre:

**EL ACREEDOR:** {empresa.razon_social}, RUC {empresa.ruc}, con domicilio en {empresa.domicilio}, representada en este acto por {empresa.representante_legal}; y

**EL DEUDOR:** {nombres y apellidos del cliente}, C.I. N.º {cédula con puntos}, con domicilio en {domicilio del cliente}, teléfono {teléfono};

{solo si hay codeudor} **EL CODEUDOR SOLIDARIO:** {nombres y apellidos}, C.I. N.º {cédula}, con domicilio en {domicilio}, teléfono {teléfono};

convienen celebrar el presente contrato de préstamo, sujeto a las siguientes cláusulas:

**PRIMERA — Objeto.** EL ACREEDOR otorga a EL DEUDOR un préstamo de dinero por la suma de **Gs. {monto_aprobado} ({MONTO EN LETRAS} GUARANÍES)**, destinado a {destino_credito}. El importe se entrega en efectivo en el local de EL ACREEDOR en la fecha de desembolso, contra la firma del comprobante de desembolso, que forma parte de este contrato.

**SEGUNDA — Plazo y forma de pago.** EL DEUDOR se obliga a devolver el préstamo en **{plazo} cuotas mensuales y consecutivas**, calculadas por el sistema francés (cuota de capital e intereses constante, más el IVA sobre los intereses de cada período). La primera cuota vence el **{primera_fecha_vencimiento}** y las siguientes el mismo día de cada mes. Cuando un vencimiento cae en sábado, domingo o feriado, se traslada al siguiente día hábil. El detalle de cada cuota (fecha, capital, interés, IVA y total) consta en el **Plan de Pagos N.º {identificador_operacion}**, que forma parte de este contrato. Los pagos se realizan en efectivo en el local de EL ACREEDOR.

**TERCERA — Tasas y costo del crédito.** El préstamo devenga una **Tasa Efectiva Anual (TEA) de {tea} %**, equivalente a una Tasa Nominal Anual (TNA) de {tna} %, y un **Costo Financiero Total (CFT) de {cft} %** anual. La tasa pactada no supera la tasa máxima vigente publicada por el Banco Central del Paraguay a la fecha de este contrato. El total a pagar, incluyendo capital, intereses e IVA, asciende a **Gs. {total a pagar} ({TOTAL EN LETRAS} GUARANÍES)**.

**CUARTA — Sin cargos adicionales.** La presente operación no incluye comisiones, gastos administrativos ni cargos adicionales; el costo total del crédito está compuesto únicamente por intereses e IVA sobre intereses.

**QUINTA — Mora.** La mora se produce de pleno derecho por el solo vencimiento de cualquier cuota impaga, sin necesidad de interpelación judicial ni extrajudicial. Desde ese momento y hasta el pago, el capital vencido devenga un **interés moratorio** a la misma tasa efectiva anual pactada ({tea} %) y un **interés punitorio** equivalente al {credito.porcentaje_punitorio} % del interés moratorio, ambos con su IVA. Los pagos se imputan en este orden: interés moratorio, interés punitorio, interés compensatorio y, por último, capital.

**SEXTA — Pago anticipado.** EL DEUDOR puede cancelar total o parcialmente el préstamo en cualquier momento, sin penalidad y sin pagar intereses de períodos futuros (Ley 6624/2020). La cancelación total se liquida por el capital pendiente más los intereses devengados a la fecha de pago. El pago anticipado parcial se aplica al capital, mantiene el monto de la cuota y reduce la cantidad de cuotas.

**SÉPTIMA — Pagaré.** En garantía de las obligaciones de este contrato, EL DEUDOR{ y EL CODEUDOR} suscribe{n} un pagaré a la orden de EL ACREEDOR por el total a pagar, con vencimiento en la fecha de la última cuota. Cancelado el préstamo, EL ACREEDOR devuelve el pagaré y entrega la constancia de cancelación.

**OCTAVA — Vencimiento anticipado.** La falta de pago de **{credito.cuotas_alerta_garante} cuotas** vencidas faculta a EL ACREEDOR a declarar vencidos todos los plazos y exigir el saldo total adeudado, con los intereses devengados hasta la fecha, pudiendo ejecutar el pagaré.

{solo si hay codeudor} **NOVENA — Codeudor solidario.** EL CODEUDOR se constituye en codeudor solidario, liso, llano y principal pagador de todas las obligaciones de EL DEUDOR bajo este contrato, renunciando a los beneficios de excusión y división.

**{NOVENA/DÉCIMA} — Información crediticia.** EL DEUDOR{ y EL CODEUDOR} autoriza{n} expresamente a EL ACREEDOR a consultar y reportar su información crediticia a burós de información (Informconf), conforme a la autorización firmada que obra en su legajo y a la Ley 6534/2020 de protección de datos crediticios.

**{DÉCIMA/UNDÉCIMA} — Domicilio y jurisdicción.** Las partes constituyen domicilio en los indicados en este contrato, donde se tendrán por válidas todas las notificaciones, y se someten a los juzgados y tribunales de la ciudad de Asunción.

En prueba de conformidad, se firman dos ejemplares de un mismo tenor y a un solo efecto, en el lugar y fecha indicados.

**Bloque de firmas (al final, en una fila con dos o tres columnas):**

| | | |
|---|---|---|
| ______________________ | ______________________ | ______________________ |
| **EL ACREEDOR** | **EL DEUDOR** | **EL CODEUDOR** (solo si hay) |
| {empresa.representante_legal} | {nombre del cliente} | {nombre del codeudor} |
| p/ {empresa.razon_social} | C.I. {cédula} | C.I. {cédula} |

**Recuadro resumen (arriba, después del título, antes de las cláusulas).** Tabla de 2 columnas con fondo gris claro: Monto del préstamo · Plazo · Cuota (1.ª, con IVA) · TEA / TNA · CFT · Total a pagar · Primer vencimiento · Último vencimiento. Así el cliente ve lo esencial de un vistazo.

---

## 2. PAGARÉ (`pagare.jrxml`)

**Una sola página.** Diseño sobrio, con recuadro en el borde.

**Título:** `PAGARÉ A LA ORDEN` — a la derecha, recuadro `N.º {identificador_operacion}` y debajo `Gs. {total a pagar}` en grande y negrita.

**Texto:**

Vence el **{fecha de la última cuota}**.

Lugar y fecha de emisión: Asunción, {fecha de formalización}.

**PAGARÉ** (pagaremos) sin protesto a **{empresa.razon_social}** (RUC {empresa.ruc}), o a su orden, en su domicilio de {empresa.domicilio}, la suma de **GUARANÍES {TOTAL EN LETRAS} (Gs. {total a pagar})**, por igual valor recibido en préstamo a nuestra entera satisfacción.

Este pagaré se emite en garantía del Contrato de Préstamo N.º {identificador_operacion}, pagadero en {plazo} cuotas según el Plan de Pagos. La falta de pago de {credito.cuotas_alerta_garante} cuotas vencidas producirá el vencimiento anticipado de este pagaré por el saldo adeudado. En caso de mora se aplicarán los intereses moratorios y punitorios pactados en el contrato. Se renuncia al protesto y a la presentación para el pago. Para todos los efectos, las partes se someten a la jurisdicción de los tribunales de Asunción.

**Firmas:**

| | |
|---|---|
| ______________________ | ______________________ (solo si hay codeudor) |
| **DEUDOR (librador)** | **CODEUDOR SOLIDARIO** |
| {nombre} — C.I. {cédula} | {nombre} — C.I. {cédula} |
| Domicilio: {domicilio} | Domicilio: {domicilio} |

---

## 3. PLAN DE PAGOS (`plan_pagos.jrxml`)

**Título:** `PLAN DE PAGOS` — `Operación N.º {identificador_operacion}`

**Bloque de datos (dos columnas):**
- Cliente: {nombre} — C.I. {cédula}
- Codeudor: {nombre} — C.I. {cédula} (solo si hay)
- Monto del préstamo: Gs. {monto} ({en letras})
- Plazo: {n} cuotas mensuales · Sistema francés
- TEA {tea} % · TNA {tna} % · CFT {cft} %
- Fecha de formalización: {fecha}

**Tabla de cuotas (una fila por cuota, encabezado repetido en cada página, filas alternadas en gris muy claro, números alineados a la derecha):**

| N.º | Vencimiento | Capital | Interés | IVA 10 % | Total cuota | Saldo de capital |
|---|---|---|---|---|---|---|
| 1 | 26/10/2026 | 149.743 | 40.236 | 4.024 | 194.003 | 1.850.257 |

**Fila de totales (en negrita, al final):** suma de Capital, Interés, IVA y Total cuota. El total de "Total cuota" debe coincidir exactamente con el total a pagar del contrato y del pagaré (sale del motor, no se recalcula).

**Notas al pie de la tabla (texto chico):**
1. Los pagos se realizan en efectivo en el local de {empresa.nombre_comercial}. Si el vencimiento cae en día inhábil, se paga el siguiente día hábil sin recargo.
2. Pasado el vencimiento se cobran intereses moratorios y punitorios según la cláusula QUINTA del contrato.
3. Puede cancelar o adelantar pagos en cualquier momento sin penalidad ni intereses futuros (Ley 6624/2020).

**Al final:** `Recibí copia de este plan de pagos.` ______________________ {nombre del cliente} — C.I. {cédula}

---

## 4. Datos que Java entrega a cada reporte (parámetros)

Todos como parámetros ya formateados o como `JRBeanCollectionDataSource` para la tabla de cuotas:

- Empresa: razón social, nombre comercial, RUC, domicilio, teléfono, WhatsApp, correo, registro BCP, representante legal (de `parametros_sistema`).
- Operación: identificador_operacion, fecha de formalización (en número y en letras), monto, plazo, destino, TEA, TNA, CFT, cuota 1.ª con IVA, total a pagar, primer y último vencimiento; los importes, también en letras.
- Personas: cliente y codeudor (nombre, cédula con puntos, domicilio, teléfono), más un indicador `tieneCodeudor`.
- Parámetros de mora: porcentaje punitorio, cuotas para vencimiento anticipado (`credito.cuotas_alerta_garante`).
- Plan: lista de cuotas (número, vencimiento, capital, interés, IVA, total, saldo) y los totales.
- Pie: nombre del usuario que emite, fecha y hora de emisión.
