# Requerimientos funcionales del anteproyecto (entregado y firmado)

| ID | Módulo | Descripción |
|---|---|---|
| RF01 | Clientes y solicitudes | El sistema debe permitir simular un préstamo indicando monto, plazo y fecha del primer vencimiento, y mostrar e imprimir el plan de cuotas sin registrar ningún dato. |
| RF02 | Clientes y solicitudes | El sistema debe permitir registrar clientes con sus datos personales, domicilio, situación laboral, ingresos, deudas y referencias. |
| RF03 | Clientes y solicitudes | El sistema debe permitir cargar el expediente de documentos del cliente según su situación laboral, e indicar qué documentos faltan y si la cédula está vencida. |
| RF04 | Clientes y solicitudes | El sistema debe permitir registrar la autorización firmada del cliente y la consulta al buró de información crediticia, y no permitir la consulta sin autorización. |
| RF05 | Clientes y solicitudes | El sistema debe permitir crear la solicitud de crédito y calcular la cuota, la tasa, el costo financiero total y el porcentaje de afectación del ingreso, sin permitir que la cuota supere el 40 % del ingreso. |
| RF06 | Clientes y solicitudes | El sistema debe permitir enviar la solicitud a aprobación solo cuando estén completos los requisitos previos, y permitir cancelarla registrando el motivo. |
| RF07 | Aprobación | El sistema debe mostrar al gerente las solicitudes pendientes de aprobación en orden de llegada y el historial de las solicitudes ya decididas. |
| RF08 | Aprobación | El sistema debe mostrar el resumen de cada solicitud: datos del cliente (edad, zona, situación laboral, empleador y otras deudas), condiciones del préstamo, afectación del ingreso y consultas al buró, con un aviso si el buró indica mora. |
| RF09 | Aprobación | El sistema debe permitir aprobar la solicitud y registrar el usuario que la aprobó y la fecha. |
| RF10 | Aprobación | El sistema debe permitir rechazar la solicitud eligiendo un motivo de una lista y registrar el usuario que la rechazó y la fecha. |
| RF11 | Formalización | El sistema debe mostrar las solicitudes aprobadas pendientes de formalizar y permitir fijar la fecha del primer vencimiento dentro del rango permitido, teniendo en cuenta los feriados y los fines de semana. |
| RF12 | Formalización | El sistema debe generar en PDF el contrato, el pagaré y el plan de pagos del préstamo, con un número de operación único. |
| RF13 | Formalización | El sistema debe permitir cargar los documentos firmados escaneados y no permitir el desembolso hasta que el checklist de firmados esté completo. |
| RF14 | Formalización | El sistema debe permitir al gerente anular un préstamo antes del desembolso registrando el motivo. |
| RF15 | Desembolso y caja | El sistema debe permitir a la cajera abrir la caja del día con un fondo inicial, y no permitir operar si quedó abierta una caja de un día anterior. |
| RF16 | Desembolso y caja | El sistema debe permitir el desembolso solo si el checklist de firmados está completo, los documentos están vigentes, la caja del día está abierta y el saldo de la caja es suficiente. |
| RF17 | Desembolso y caja | El sistema debe emitir el comprobante de desembolso para la firma del cliente antes de confirmar la entrega del efectivo, y registrar la salida en la caja. |
| RF18 | Desembolso y caja | El sistema debe registrar los movimientos de la caja (entregas de efectivo del gerente, desembolsos y cobros), mostrar el saldo teórico y permitir reimprimir los comprobantes. |
| RF19 | Desembolso y caja | El sistema debe permitir cerrar la caja con arqueo, comparando el efectivo contado con el saldo teórico y exigiendo el motivo cuando hay diferencia. |
| RF20 | Cobranza y mora | El sistema debe permitir buscar un préstamo por cliente, cédula o número de operación, y mostrar una lista de trabajo con los préstamos que vencen hoy, los que están en mora y los que vencen en los próximos 7 días. |
| RF21 | Cobranza y mora | El sistema debe calcular la deuda exigible a la fecha, con las cuotas vencidas, el interés moratorio y punitorio según los días de atraso, y el IVA correspondiente. |
| RF22 | Cobranza y mora | El sistema debe permitir registrar el cobro en efectivo de una o más cuotas, calculando el vuelto. |
| RF23 | Cobranza y mora | El sistema debe emitir el recibo de cada cobro y la factura de los intereses con su IVA. |
| RF24 | Cobranza y mora | El sistema debe mostrar los préstamos en mora con sus días de atraso y permitir registrar las gestiones de cobranza (llamada, mensaje o visita), incluidas las promesas de pago con su fecha y monto. |
| RF25 | Cobranza y mora | El sistema debe permitir a la cajera solicitar la reversión de un cobro y al gerente autorizarla o rechazarla. |
| RF26 | Cancelación | El sistema debe calcular el monto para cancelar el préstamo por anticipado, cobrando solo los intereses devengados hasta la fecha y sin intereses futuros, y registrar la cancelación total. |
| RF27 | Cancelación | El sistema debe permitir adelantar capital, mostrando antes de confirmar el interés devengado, el capital aplicado, el saldo que queda y la nueva fecha de la próxima cuota. |
| RF28 | Cancelación | El sistema debe emitir la constancia de cancelación y dejar el préstamo como finalizado, sin deuda pendiente. |
| RF29 | Cancelación | El sistema debe gestionar el cierre documental del préstamo: generar la constancia de devolución del pagaré, cargar el escaneo firmado por el cliente y marcar el cierre como completo. |
| RF30 | Informes | El sistema debe mostrar al ingresar un panel con los indicadores del día según el perfil: cobros de hoy, préstamos en mora, promesas de pago, efectivo en caja, cartera, cobrado en la semana y en el mes, y solicitudes pendientes de aprobar. |
| RF31 | Informes | El sistema debe generar informes para el dueño: resumen financiero, resumen mensual, cartera y proyección de fondos a 30, 60 y 90 días. |
| RF32 | Informes | El sistema debe generar informes de la operación y la cobranza: solicitudes, aprobaciones y rechazos, desembolsos, cobros, vencimientos, caja, morosidad por tramo, gestiones de cobranza e incobrables. |
| RF33 | Informes | El sistema debe generar el informe de las facturas emitidas en el mes con su IVA y el de las facturas anuladas, para la liquidación de impuestos. |
| RF34 | Informes | El sistema debe mostrar el estado de cuenta de cada préstamo con su historial completo y permitir reimprimir sus documentos. |
| RF35 | Informes | El sistema debe permitir filtrar los informes por rango de fechas y exportarlos en PDF y en Excel (CSV). |
| RF36 | Seguridad | El sistema debe permitir el ingreso con usuario y contraseña, y obligar a cambiar la contraseña en el primer ingreso (mínimo 8 caracteres, con al menos una letra y un número). |
| RF37 | Seguridad | El sistema debe permitir al administrador crear usuarios, asignarles un perfil (administrador, gerente, oficial de crédito o cajera), y bloquearlos, inactivarlos, reactivarlos o restablecer su contraseña. |
| RF38 | Seguridad | El sistema debe mostrar a cada usuario solo las opciones y los informes que corresponden a su perfil. |
| RF39 | Seguridad | El sistema debe permitir al administrador modificar los parámetros del negocio (tasa, montos, plazos y límite de afectación), los feriados y los límites legales de tasa, y guardar el historial de cada cambio con el valor anterior, el valor nuevo, quién lo hizo y cuándo. |
| RF40 | Seguridad | El sistema debe registrar en la auditoría las operaciones realizadas, con fecha, hora y usuario, y permitir consultarlas con filtros. |

# Requerimientos no funcionales del anteproyecto (entregado y firmado)

## Infraestructura a considerar necesarias para la implementación

| ID | Tipo | Descripción |
|---|---|---|
| RNF01 | Hardware del servidor | El sistema debe instalarse en una computadora del local que funcione como servidor, con procesador Intel Core i5 o equivalente, 8 GB de memoria RAM y disco SSD de 256 GB como mínimo. |
| RNF02 | Hardware de los usuarios | El sistema debe poder usarse desde las computadoras del gerente, el oficial de crédito y la cajera, con 4 GB de memoria RAM como mínimo, sin instalar ningún programa adicional. |
| RNF03 | Software del servidor | El sistema debe funcionar sobre Java 8 o superior, el servidor de aplicaciones Apache Tomcat 9 y la base de datos PostgreSQL 17, en Windows o Linux. |
| RNF04 | Software de los usuarios | El sistema debe ser accesible desde navegadores web modernos (Google Chrome, Microsoft Edge o Mozilla Firefox). |
| RNF05 | Red | El sistema debe funcionar en la red local del negocio, sin depender de una conexión a internet para operar. |
| RNF06 | Periféricos | El negocio debe contar con una impresora para emitir contratos, pagarés, recibos, facturas y comprobantes, y con un escáner para cargar los documentos firmados. |
| RNF07 | Energía | El servidor debe estar conectado a una UPS para evitar la pérdida de información ante cortes de energía. |

## Requerimientos de calidad

| ID | Tipo | Descripción |
|---|---|---|
| RNF08 | Seguridad | El sistema debe guardar las contraseñas cifradas, de forma que nadie pueda leerlas, ni siquiera desde la base de datos. |
| RNF09 | Seguridad | El sistema debe bloquear la cuenta después de 3 intentos fallidos de ingreso y mostrar el mismo mensaje sin revelar si el usuario existe. |
| RNF10 | Seguridad | El sistema debe cerrar la sesión después de 30 minutos de inactividad, avisando al usuario 2 minutos antes. |
| RNF11 | Seguridad | El sistema debe controlar los permisos de cada perfil en el servidor, aunque el usuario escriba la dirección de una pantalla a mano, y rechazar formularios enviados desde fuera del sistema. |
| RNF12 | Integridad | El sistema no debe permitir borrar operaciones registradas. Las correcciones se hacen por anulación o reversión, con motivo, autorización del gerente y registro en la auditoría. |
| RNF13 | Usabilidad | El sistema debe tener una interfaz en español, mostrar los montos con separador de miles, pedir confirmación antes de cada operación que no se puede deshacer y mostrar mensajes claros. |
| RNF14 | Rendimiento | El sistema debe responder en menos de 3 segundos en las operaciones habituales. |
| RNF15 | Disponibilidad | El sistema debe estar disponible durante todo el horario de atención del local. |
| RNF16 | Respaldo | El sistema debe contar con una copia de seguridad diaria de la base de datos, guardada fuera del servidor. |
| RNF17 | Mantenibilidad | El sistema debe permitir cambiar los parámetros del negocio (tasa, montos, plazos, límites) desde sus propias pantallas, sin modificar el programa. |
| RNF18 | Normativa | El sistema debe respetar el tope legal de la tasa de interés, calcular la cancelación anticipada sin intereses futuros (Ley N.º 6624/2020) y facturar los intereses con su IVA (Ley N.º 6380/2019). |
