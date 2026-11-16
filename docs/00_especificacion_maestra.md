**SISTEMA DE GESTIÓN DE CRÉDITOS PERSONALES Y COBRANZAS (SGCPC)**

# Documento 00 – Especificación Maestra de Desarrollo

Guía rectora del análisis, diseño y construcción de la versión 1

| **Dato** | **Detalle** |
|---|---|
| Cliente | Capital Crédito Paraguay S.R.L. (datos institucionales ficticios para prueba y defensa) |
| Autor | Joel Acosta – Analista de sistemas |
| Versión / fecha | 1.2 – 23/09/2026 (v1.1: decisiones del cliente D-02, D-04, D-05 · v1.2: entrega, servidor, conexión, reportes y herramientas D-06 a D-09) |
| Estado | Aprobado para desarrollo. Decisiones pendientes cerradas; sujeto a la validación final del cliente en la prueba en el local |
| Fuentes | Documentos 01 a 11 (versión FINAL), Acta de reunión N.º 2 (23/09/2026), Simulador de tasas (Doc. 14), datos institucionales entregados por el cliente |
| Prevalencia | Donde este documento difiera de los Documentos 01 a 11, prevalece este documento. Las diferencias están listadas en el Anexo A. |

**AVISO (07/10/2026):** la numeración de RF de este documento es interna y anterior al anteproyecto. Los requerimientos funcionales OFICIALES son los 40 del anteproyecto entregado, en `docs/RF_ANTEPROYECTO.md`. Ante cualquier diferencia, manda el anteproyecto.

> **Cómo usar este documento**
> Es la única guía para programar la versión 1. Antes de escribir código de un módulo, leer su sección de reglas (4), cálculo (6), proceso (8) y datos (10).
> Si durante el desarrollo aparece una situación que el documento no resuelve: **no se inventa la regla en el código**. Se anota en la sección 19.1 (Registro de decisiones), se decide y recién después se programa.
> Los Documentos 01 a 11 siguen siendo la base de la tesis. Una vez validado el sistema con el cliente, se actualizarán con el Anexo A.

**Contenido**

1\. Contexto del cliente y problema a resolver

2\. Alcance de la versión 1

3\. Actores, perfiles y permisos

4\. Reglas de negocio consolidadas

5\. Parámetros del sistema

6\. Motor financiero (M06): especificación de cálculo

7\. Estados y transiciones

8\. Procesos operativos (flujo del día en el local)

9\. Catálogo de requerimientos funcionales

10\. Modelo de datos

11\. Documentos imprimibles

12\. Pantallas y navegación

13\. Reportes y panel

14\. Arquitectura, tecnología y requisitos no funcionales

15\. Estructura del proyecto y convenciones

16\. Datos de prueba

17\. Plan de desarrollo paso a paso

18\. Pruebas y aceptación

19\. Riesgos, supuestos y pendientes

Anexo A. Cambios respecto de los Documentos 01 a 11

Anexo B. Glosario

Anexo C. Casos financieros de referencia

# 1. Contexto del cliente y problema a resolver

## 1.1. Situación anterior (AS-IS)

El cliente prestó dinero de manera informal durante años en su barrio, a personas conocidas o recomendadas. Hoy quiere formalizarse: abrir un local pequeño en su domicilio, atender al público y operar conforme a la ley paraguaya.

| **Aspecto** | **Cómo funcionaba** |
|---|---|
| Volumen | Más de 20 préstamos activos a la vez; unos 10 clientes nuevos por mes. |
| Montos | De 500.000 Gs (primer préstamo de todo cliente nuevo) a 15.000.000 Gs. Si el cliente cumple, el siguiente puede duplicarse. |
| Interés | 10 % total sobre el monto, dividido en cuotas mensuales iguales. Ejemplo: 500.000 Gs en 3 cuotas de 183.333 Gs. |
| Cobro | Efectivo, en persona. Aceptaba pagos parciales, pagos de varias cuotas y cancelaciones totales. |
| Garantías | Ninguna. Confianza personal y conocimiento del domicilio y trabajo del cliente. |
| Registro | Cuaderno. Sin recibos ni copia de respaldo. |

## 1.2. Problemas que el sistema debe resolver

| **N.º** | **Problema del cliente** | **Cómo lo resuelve el sistema** |
|---|---|---|
| PR-01 | Errores en el cálculo manual de intereses, cuotas y saldos. | Motor financiero único (M06) que calcula plan, mora, pagos y saldos. |
| PR-02 | No sabe cuánto tiene prestado, cuánto recuperó ni cuánto gana. | Panel diario y reportes de cartera, cobranza y ganancia. |
| PR-03 | Olvida fechas de cobro y no hace seguimiento a los atrasados. | Lista de vencimientos, alertas de mora y registro de gestiones de cobro. |
| PR-04 | El cuaderno puede perderse; es la única copia. | Base de datos con respaldo y documentos escaneados dentro del sistema. |
| PR-05 | Información desordenada; no sabe quién pagó. | Estado de cuenta por cliente con cada pago, recibo y saldo. |
| PR-06 | No hay historial de puntualidad del cliente. | Historial de préstamos, atrasos y gestiones por cliente. |
| PR-07 | No entrega comprobantes; hay discusiones. | Recibo, factura y constancias impresas en cada operación. |
| PR-08 | Necesita operar de forma legal. | Tope de tasa, límites de mora, pago anticipado sin interés futuro, consentimiento para el buró, factura y conservación de registros. |

## 1.3. Objetivos medibles de la versión 1

- Registrar un préstamo completo (cliente → solicitud → aprobación → firma → desembolso) en menos de 20 minutos.

- Cobrar una cuota e imprimir recibo y factura en menos de 2 minutos.

- Calcular mora, cancelación anticipada y saldos sin intervención manual y con los resultados de referencia del Anexo C.

- Mostrar cada mañana la lista de cobros del día y de atrasados.

- Cerrar la caja del día con arqueo y rendición al gerente.

# 2. Alcance de la versión 1

## 2.1. Incluido

| **Módulo** | **Qué incluye** |
|---|---|
| M01 Seguridad y parámetros | Login, 3 perfiles, usuarios, parámetros institucionales/financieros/fiscales, feriados, auditoría, lista de verificación para la apertura. |
| M02 Clientes y expediente | Ficha única de persona (cliente o garante), documentos escaneados, consentimiento y registro manual de consulta al buró. |
| M03 Solicitudes y evaluación | Cotizador rápido, solicitud, simulación, capacidad de pago, garante, envío a aprobación. |
| M04 Aprobación | Bandeja del gerente, aprobar, rechazar o cancelar con motivo. |
| M05 Formalización | Contrato, pagaré, plan de pagos, autorización de consulta; firma, escaneo, checklist; cierre documental y devolución del pagaré. |
| M06 Motor financiero | Plan francés, IVA, tope de tasa, mora, aplicación de pagos, cancelación y anticipo. |
| M07 Caja, desembolso y cobranza | Apertura con fondo, entregas y rendiciones del gerente, desembolso, cobro, pago parcial con promesa, anticipo, cancelación, factura, recibo, reversiones, arqueo y cierre. |
| M08 Reportes y control | Panel diario, vencimientos, morosos, gestión de cobranza, cartera, caja, ganancia, auditoría, reportes para cumplimiento. |

## 2.2. Excluido de la versión 1

- El sistema emite factura con timbrado; la integración con el sistema de facturación electrónica SIFEN (DNIT/e-Kuatia: KuDE, CDC, QR, XML firmado) queda fuera del alcance y se propone como trabajo futuro.

- Transferencias, cheques, billeteras y cobradores a domicilio: todo es efectivo en el local.

- Recordatorios automáticos por WhatsApp o SMS (versión 2).

- Refinanciación de préstamos y castigo contable de incobrables (solo reporte de más de 180 días).

- Gastos del local, sueldos y contabilidad general (los maneja el dueño fuera del sistema).

- Integración automática con Informconf/Equifax: la consulta se registra manualmente.

- Portal o app para clientes, sucursales, varias monedas, varias cajas.

## 2.3. Prioridades (MoSCoW)

| **Prioridad** | **Funciones** |
|---|---|
| Debe (para la prueba en el local) | Login y perfiles · Clientes y garantes · Cotizador · Solicitud y aprobación · Contrato, pagaré y plan · Caja con fondo y rendición · Desembolso · Cobro con recibo y factura · Mora · Pago parcial con promesa · Cancelación anticipada · Vencimientos y morosos · Arqueo y cierre · Panel diario · Datos de prueba |
| Debería | Anticipo parcial con nuevo plan · Reversiones · Gestión de cobranza · Constancia de devolución de pagaré · Reportes mensuales · Auditoría consultable |
| Podría | Exportaciones para BCP/SEPRELAD · Reporte de incobrables · Respaldo desde la pantalla |
| No en V1 | SIFEN · WhatsApp/SMS · Refinanciación · Transferencias |

# 3. Actores, perfiles y permisos

| **Perfil** | **Persona en el local** | **Responsabilidad** |
|---|---|---|
| GERENTE | El dueño | Aprueba o rechaza, entrega y recibe el efectivo de la caja, autoriza reversiones y diferencias de caja, administra usuarios y parámetros, controla reportes. |
| OFICIAL_CREDITO | Oficial de crédito | Atiende y cotiza, registra clientes y garantes, arma la solicitud, formaliza, registra gestiones de cobranza a morosos. |
| CAJERO | Cajera | Abre y cierra caja, desembolsa, cobra, imprime recibo y factura, registra pagos parciales, solicita reversiones. |
| Cliente / Garante | Externo | No usa el sistema. Aporta datos, firma, recibe y paga en efectivo. |

## 3.1. Matriz de permisos

*X = puede hacerlo · C = solo consulta · — = no puede. Todo permiso se valida en el servidor (FiltroAutorizacion).*

| **Acción** | **Gerente** | **Oficial** | **Cajero** | **Administrador** |
|---|---|---|---|---|
| Cotizar préstamo (sin datos del cliente) | X | X | X | X |
| Registrar / editar cliente o garante | X | X | C | X |
| Cargar documentos y consentimiento; registrar consulta al buró | X | X | — | X |
| Crear, editar y enviar solicitud; cancelar BORRADOR | — | X | — | X |
| Aprobar / rechazar / cancelar solicitud pendiente | X | — | — | X |
| Generar documentos, registrar firma y checklist | X | X | — | X |
| Anular préstamo antes del desembolso | X | — | — | X |
| Abrir / cerrar caja, arqueo | C | — | X | X |
| Entregar efectivo a caja / recibir rendición | X (confirma) | — | X (registra) | X |
| Desembolsar | — | — | X | X |
| Cobrar, pago parcial, cancelación, anticipo | — | — | X | X |
| Solicitar reversión | — | — | X | X |
| Autorizar reversión o diferencia de caja | X | — | — | X |
| Registrar gestión de cobranza (llamada, promesa) | X | X | X (al cobrar) | X |
| Ver panel y reportes | X | Parcial (su trabajo) | Parcial (caja) | X |
| Usuarios (alta, editar, bloquear, inactivar, reactivar, restablecer contraseña, informes) | — | — | — | X |
| Parámetros, feriados, límites de tasa (historial incluido) | C | — | — | X |
| Consultar auditoría | X | — | — | X |

> **Ausencia del gerente**
> Si el gerente no está, las solicitudes quedan en PENDIENTE_APROBACION y las reversiones en espera. No se delega la aprobación en la V1 (el local funciona en el domicilio del gerente). El gerente no comparte su contraseña: cada autorización queda registrada con su propio usuario.

> **D7 / D-151 (decisiones posteriores a la V1 original, ver AVANCE.md)**
> El perfil **Administrador** se agregó aparte de Gerente (D7): acceso total a todo el sistema, sin
> excepción, sin tocar en nada los permisos del Gerente. D-151 hizo una excepción puntual sobre esa
> regla general para dos filas de esta tabla: Usuarios pasó a ser exclusivo del Administrador (el
> Gerente, que antes tenía acceso total, lo perdió por completo) y Parámetros le quedó al Gerente en
> **C** (consulta: ve valores e historial, nunca modifica) en vez de X. Motivo de Joel: el
> Administrador responde legalmente por el alta/baja/edición de usuarios y actúa en presencia del
> dueño.

# 4. Reglas de negocio consolidadas

*Origen: C = pedido del cliente · L = exigencia legal · A = decisión del analista (aprobada o a validar en la prueba).*

| **Código** | **Regla** | **Origen** |
|---|---|---|
| RN-01 | Un cliente tiene como máximo un préstamo no cerrado (PENDIENTE_DESEMBOLSO, ACTIVO o EN_MORA). Puede pedir otro cuando el anterior queda FINALIZADO o ANULADO. | C |
| RN-02 | Un cliente tiene como máximo una solicitud abierta (BORRADOR, PENDIENTE_APROBACION o APROBADA sin formalizar). | A |
| RN-03 | Capacidad de pago: (cuota más alta con IVA ÷ ingreso neto comprobado) × 100 ≤ límite (parámetro, 40 %, aprobado por el cliente). Con garante: ÷ (ingreso cliente + ingreso garante). | C |
| RN-04 | **RETIRADA** (18/10/2026, commit `6e7da13` — ver `docs/funcionamiento/RF05_solicitud.md` §10). Garante, según el parámetro `credito.exigir_garante` (D-125, 29/09/2026 — antes fija): con "siempre", obligatorio si (a) el cliente no presenta comprobante de ingresos (trabajador informal), o (b) la capacidad supera el límite; con "nunca" (valor de fábrica desde D-125: la financiera solo presta a quien comprueba ingresos), nadie necesita garante — si la capacidad supera el límite sin garante posible, la solicitud se rechaza directo ("La cuota supera la capacidad de pago del cliente. Reduzca el monto o amplíe el plazo."); "según_monto" queda reservado para un umbral por monto, sin definir todavía (se comporta como "siempre"). Máximo un garante por solicitud. Cliente y garante no pueden ser la misma persona. La función de garante no se eliminó: con cualquier valor del parámetro se lo puede seguir asignando manualmente (la tarjeta y el formulario solo se ocultan en pantalla con "nunca"). | C |
| RN-05 | Cuotas mensuales iguales, sistema francés, interés sobre saldo. Plazo de 3 a 24 meses; monto de 500.000 a 15.000.000 Gs (parámetros). | C |
| RN-06 | Solo el gerente aprueba o rechaza. El sistema no aprueba automáticamente. El rechazo exige motivo. | C |
| RN-07 | El monto y plazo aprobados son los solicitados; si el gerente quiere otros, se cancela y se crea una nueva solicitud. | C |
| RN-08 | Desembolso único, en efectivo, con caja ABIERTA y efectivo suficiente. Sin descuentos: el cliente recibe el monto aprobado completo. | C/L |
| RN-09 | Todo cobro es en efectivo y con caja ABIERTA. Se registra recibido, vuelto y aplicado. | C |
| RN-10 | Aplicación del pago automática: primero la cuota vencida más antigua y dentro de ella moratorio, punitorio, interés (cada uno con su IVA) y capital. El cajero no elige. | L/A |
| RN-11 | Pago anticipado parcial o total sin cobrar intereses futuros ni penalidad (Ley 6624/2020). El anticipo parcial reduce la cantidad de cuotas y mantiene el monto de la cuota. | L |
| RN-12 | Saldo total en cero → préstamo FINALIZADO. La devolución del pagaré se registra aparte, con constancia firmada. | C/L |
| RN-13 | Nada se borra ni se edita: cobros y desembolsos se corrigen con reversión autorizada por el gerente. **Fuera del alcance del anteproyecto firmado (`docs/RF_ANTEPROYECTO.md`) en la parte de desembolsos** (decisión de Joel, 17/10/2026, auditoría de toda la base — ver `docs/base_de_datos/02_analisis_y_problemas.md §8`): el anteproyecto firmado protege el desembolso ANTES de confirmarse (RF16 checklist/caja, RF17 comprobante firmado por el cliente, RF14 anulación antes del desembolso), no con una reversión posterior — `reversiones_desembolso` se eliminó del esquema. La parte de cobros sigue vigente y cumplida (RF25, RNF12). | A |
| RN-14 | Una caja CERRADA no se reabre. Las correcciones posteriores se registran en la caja abierta del día. | A |
| RN-15 | La TEA del préstamo debe ser ≤ tope legal vigente del BCP (parámetro histórico). Si lo supera, se bloquea aprobación, formalización y desembolso. | L |
| RN-16 | Interés moratorio: tasa ≤ tasa del préstamo, solo sobre capital vencido, sin capitalizar. Punitorio: ≤ 30 % del moratorio. Ambos gravados con IVA. Valores iniciales al máximo legal, aprobados por el cliente. | L/C |
| RN-17 | No hay comisiones ni gastos aparte: el cliente conoce la cuota exacta (con IVA) antes de firmar. **`cargos_operacion` (la tabla que habría guardado esos cargos) se eliminó del esquema el 17/10/2026** (decisión de Joel, auditoría de toda la base — ver `docs/base_de_datos/02_analisis_y_problemas.md §8`): el anteproyecto firmado nunca pidió comisiones ni gastos, así que la tabla no tenía ningún requisito que cumplir, solo anticipaba una posibilidad que la política del negocio descartó. | C |
| RN-18 | No se presta a quien figura con deudas en Informconf: bloqueo automático del servidor, al enviar la solicitud a aprobación y al intentar aprobarla (17/10/2026, pedido de Joel — antes era juicio del gerente, con una confirmación reforzada "¿Aprobar igualmente?"). Si la mora se detecta recién después de enviada (una consulta nueva, posterior al envío), el intento de aprobar también se bloquea; el gerente la rechaza con el motivo "Informconf: figura con deudas" (que sigue activo en `motivos_rechazo_solicitud`). | C |
| RN-19 | Vencimientos solo en días hábiles. Si la fecha cae en sábado, domingo o feriado, pasa al siguiente día hábil. | C |
| RN-20 | Pago parcial permitido: el cajero registra obligatoriamente la fecha en que el cliente promete completar. | C |
| RN-21 | El excedente sobre lo exigible se devuelve como vuelto o, si el cliente lo pide, se aplica como anticipo a capital (RN-11). No se aplica a la próxima cuota. | C/A |
| RN-22 | **RETIRADA** (18/10/2026, commit `6e7da13` — ver `docs/funcionamiento/RF24_seguimiento_mora.md` §5/§7). Garante: el sistema alerta para contactarlo cuando el préstamo tiene 2 o más cuotas vencidas. No depende directamente de `credito.exigir_garante` (D-125) sino de si el préstamo TIENE un garante asignado — con la política en "nunca" nadie lo tiene, así que la alerta simplemente no aparece; si alguna vez se asigna uno igual (la función sigue disponible), la alerta funciona normal. | C |
| RN-23 | La caja inicia cada día con el fondo que entrega el gerente y cierra con arqueo y rendición del efectivo al gerente. Una diferencia exige motivo y revisión del gerente. | C |
| RN-24 | Consulta al buró solo con autorización firmada y escaneada del titular (o garante). | L |
| RN-25 | Toda factura muestra timbrado, vigencia, RUC y numeración 001-001-NNNNNNN correlativa sin saltos; incluye solo intereses, moratorio y punitorio gravados 10 % con IVA incluido; el capital se documenta en el recibo, no en la factura (modificada por decisión de Joel el 26/09/2026, según práctica de financieras en Paraguay). | L |
| RN-26 | Los registros de operaciones y documentos se conservan al menos 5 años; no existe borrado. | L |
| RN-27 | Los préstamos con más de 180 días de atraso aparecen en el reporte de incobrables (sin cambiar su estado). | A |
| RN-28 | **RETIRADA** (18/10/2026, commit `6e7da13` — ver `docs/funcionamiento/RF05_solicitud.md` §10). Una persona que tiene deuda con la empresa no puede ser codeudora: tiene deuda si tiene un préstamo no cerrado (estado distinto de FINALIZADO/ANULADO_ANTES_DESEMBOLSO) o una solicitud propia todavía abierta (BORRADOR/PENDIENTE_APROBACION/APROBADA, sin desembolsar). Se valida al asignar codeudor, al enviar a aprobación y al aprobar -- con cualquier valor de `credito.exigir_garante` (D-125): si alguna vez se asigna un codeudor, esta regla se aplica igual, lo exija o no la política vigente. | C |

# 5. Parámetros del sistema

Ningún valor de esta sección se escribe en el código. Se guardan en parametros_sistema (con fecha de vigencia) y los administra el gerente en la pantalla P12. Los valores institucionales son ficticios para la prueba y la defensa; antes de atender clientes reales se reemplazan por los reales (ver 16.3).

## 5.1. Datos institucionales (ficticios)

| **Clave** | **Valor inicial** |
|---|---|
| empresa.razon_social | Capital Crédito Paraguay S.R.L. |
| empresa.nombre_comercial | Capital Crédito |
| empresa.ruc | 80123456-5 | *(corregido el 01/11/2026 con el valor vigente de `parametros_sistema`; antes figuraba 80123456-7)* |
| empresa.actividad | Otorgante de Crédito Dinerario (OCD) / Casa de Crédito |
| empresa.domicilio | Tte. Rojas Silva 1245 c/ Sargento Gauto, Barrio Obrero, Asunción |
| empresa.telefono / empresa.whatsapp | \(021\) 941 890 / (0972) 552 233 |
| empresa.correo | atencion@capitalcredito.com.py |
| empresa.representante_legal | Ramón Benítez – C.I. 2.845.117 |
| empresa.registro_bcp | Res. SB N.º 0147/2026 |
| empresa.registro_siro | SIRO-2026-00893 |

## 5.2. Parámetros fiscales

| **Clave** | **Valor inicial** | **Nota** |
|---|---|---|
| fiscal.timbrado_numero | 16842795 | Ficticio |
| fiscal.timbrado_vigencia_desde / hasta | 01/09/2026 – 31/08/2027 | La factura se bloquea fuera de vigencia. *(corregido el 01/11/2026 con el valor vigente de `parametros_sistema`; antes figuraba 01/10/2026 – 30/09/2027)* |
| fiscal.establecimiento / punto_expedicion | 001 / 001 | |
| fiscal.factura_numero_desde / hasta | 1 / 9999999 | Numeración correlativa |
| fiscal.tipo_comprobante | AUTOIMPRESOR | SIFEN en V2 |
| fiscal.iva_intereses | 10 % | Supuesto a confirmar con contador |
| fiscal.iva_capital | Exento | |

## 5.3. Parámetros financieros y operativos

| **Clave** | **Valor inicial** | **Regla** |
|---|---|---|
| credito.tea_tope_legal | 27,39 % (vigencia sept. 2026) | Tabla limites_tasa_usura con histórico. Verificar valor BCP. |
| credito.tea_empresa | 27,00 % | Debe ser ≤ tope (RN-15) |
| credito.tasa_moratoria_anual | = tea del préstamo | ≤ tasa compensatoria (RN-16; aprobado D-04) |
| credito.porcentaje_punitorio | 30 % | Del moratorio; máximo legal 30 % |
| credito.dias_gracia_mora | 0 | Días sin mora luego del vencimiento |
| credito.monto_minimo / maximo | 500.000 / 15.000.000 Gs | RN-05 |
| credito.plazo_minimo / maximo | 3 / 24 meses | RN-05 |
| credito.limite_afectacion | 40 % | RN-03 (confirmado por el cliente, D-05) |
| credito.cuotas_alerta_garante | 2 | RN-22 |
| credito.exigir_garante | nunca | RN-04 (D-125): "nunca" / "según_monto" (reservado) / "siempre" |
| credito.edad_minima / maxima | 22 / 71 años | D-127: política de crédito parametrizable, se valida al crear la solicitud |
| credito.departamentos_habilitados | Asunción,Central | D-127: nombres de `departamentos` separados por coma |
| credito.situaciones_laborales_admitidas | todas menos INDEPENDIENTE y DESEMPLEADO | D-127: códigos separados por coma |
| credito.dias_incobrable | 180 | RN-27 |
| caja.diferencia_tolerada | 0 Gs | Toda diferencia exige motivo |
| seguridad.intentos_login | 3 | Luego BLOQUEADA |
| seguridad.minutos_inactividad | 30 | |
| archivos.tamano_maximo_mb | 5 | PDF, JPG, PNG |

# 6. Motor financiero (M06): especificación de cálculo

> **Regla de oro**
> Todas las fórmulas de esta sección viven **solo** en ServicioMotorFinanciero. Pantallas, servlets, DAO, SQL y reportes Jasper **muestran** resultados; nunca calculan. Se usa BigDecimal con escala 10 en cálculos intermedios y redondeo HALF_UP a guaraní entero en cada importe que se guarda o se muestra.

## 6.1. Tasas

```
i (tasa mensual efectiva) = (1 + TEA)^(1/12) − 1
TNA (informativa) = i × 12
CFT (costo financiero total anual) = TIR mensual de los flujos del cliente
(recibe monto; paga cuotas con IVA), anualizada: (1 + tir)^12 − 1
Ejemplo: TEA 27 % → i = 2,011776 % → TNA = 24,14 %
```

Validación RN-15: se compara la TEA del préstamo con el tope vigente a la fecha de aprobación (y se repite al formalizar y desembolsar). Se guarda en el préstamo el id del límite usado.

## 6.2. Plan de pagos (sistema francés)

```
C (cuota sin IVA) = redondear( P × i / (1 − (1 + i)^−n) )
Para cada cuota k = 1..n:
interés_k = redondear( saldo_{k−1} × i )
capital_k = C − interés_k (en la última cuota: capital_n = saldo_{n−1})
cuota_k = interés_k + capital_k (la última puede diferir por redondeo)
iva_k = redondear( interés_k × 10 % )
total_k = cuota_k + iva_k ← lo que paga el cliente
saldo_k = saldo_{k−1} − capital_k
```

Vencimientos: la primera fecha la elige el oficial (día hábil). La cuota k vence el mismo día del mes k−1 posterior; si ese día no existe (p. ej. 31) se usa el último día del mes. Luego se aplica RN-19 (siguiente día hábil según tabla feriados). El interés de cada cuota es por período mensual completo, aunque la fecha se desplace por feriado.

### Caso de referencia BF-01 (500.000 Gs, 3 meses, TEA 27 %, primer vencimiento 02/11/2026)

| **N.º** | **Vence** | **Saldo inicial** | **Interés** | **Capital** | **Cuota** | **IVA** | **Total** | **Saldo final** |
|---|---|---|---|---|---|---|---|---|
| 1 | 02/11/2026 | 500.000 | 10.059 | 163.358 | 173.417 | 1.006 | 174.423 | 336.642 |
| 2 | 02/12/2026 | 336.642 | 6.772 | 166.645 | 173.417 | 677 | 174.094 | 169.997 |
| 3 | 04/01/2027\* | 169.997 | 3.420 | 169.997 | 173.417 | 342 | 173.759 | 0 |
| Total | | | 20.251 | 500.000 | 520.251 | 2.025 | 522.276 | |

*\* 02/01/2027 cae sábado → se desplaza al lunes 04/01/2027 (RN-19).*

## 6.3. Capacidad de pago

```
afectación = (total de la cuota más alta (cuota 1 con IVA) + cuota mensual de otras deudas del
              cliente y del garante, si hay) / ingreso neto comprobado × 100
con garante: / (ingreso cliente + ingreso garante)
D-127 (Tanda B, Opción A elegida por Joel): la cuota de otras deudas se SUMA a la cuota nueva antes
de dividir (nunca se resta del ingreso, para no arriesgar un denominador cero/negativo). Los otros
ingresos y los gastos fijos que se registran en la ficha del cliente son solo informativos -- no
entran en esta fórmula.
Si afectación > límite → exige garante; si aun así supera → no se puede enviar a aprobación.
```

## 6.4. Mora: moratorio y punitorio

Una cuota está vencida si hoy \> fecha_vencimiento + días de gracia y tiene saldo. No existe un estado "VENCIDA" guardado: se deriva. La mora se calcula por cuota y se acumula en tramos: cada vez que la cuota recibe un pago, se "corta" la mora hasta esa fecha y lo no pagado queda guardado como pendiente.

```
días = fecha_cálculo − max(fecha_vencimiento, fecha_corte_mora)
base = capital pendiente de la cuota (nunca intereses → no se capitaliza)
moratorio = redondear( base × ((1 + tasa_moratoria_anual)^(días/365) − 1) )
punitorio = redondear( moratorio × porcentaje_punitorio )
iva_mora = redondear( moratorio × 10 % ) ; iva_punitorio = redondear( punitorio × 10 % )
Totales a la fecha = pendiente guardado (cuota.moratorio_pendiente, punitorio_pendiente) + tramo actual
Al aplicar un pago: se suma el tramo al pendiente, fecha_corte_mora = fecha del pago, y luego se aplica el pago.
```

### Caso de referencia BF-06 (cuota 1 del BF-01 pagada 15 días tarde, el 17/11/2026)

| **Concepto** | **Cálculo** | **Importe (Gs)** |
|---|---|---|
| Base (capital de la cuota) | | 163.358 |
| Moratorio | 163.358 × (1,27^(15/365) − 1) | 1.613 |
| IVA moratorio | 1.613 × 10 % | 161 |
| Punitorio | 1.613 × 30 % | 484 |
| IVA punitorio | 484 × 10 % | 48 |
| Cuota con IVA | 173.417 + 1.006 | 174.423 |
| Total a pagar el 17/11/2026 | | 176.729 |

## 6.5. Aplicación de un pago (RN-10)

1.  Calcular a la fecha la mora de todas las cuotas vencidas (6.4).

2.  Ordenar las cuotas vencidas de la más antigua a la más nueva.

3.  Para cada cuota, aplicar en este orden hasta agotar el dinero: moratorio + su IVA → punitorio + su IVA → interés + su IVA → capital. Si el dinero no alcanza para un concepto completo, se reparte: neto = redondear(monto / 1,10), IVA = monto − neto.

4.  Si queda dinero y no hay vencidas, se aplica a la **cuota del período en curso** (la próxima a vencer) por su importe contractual, en el mismo orden.

5.  Si todavía queda dinero: el cajero elige **devolver como vuelto** (opción por defecto) o **aplicarlo como anticipo a capital** (6.7). Nunca se cobra por adelantado el interés de cuotas futuras.

6.  Cada importe aplicado se guarda en **aplicaciones_pago** (cuota, concepto, neto, IVA). Se actualizan saldos de la cuota y estado de pago (PENDIENTE, PARCIAL, PAGADA).

7.  Se recalcula el estado del préstamo: EN_MORA si queda alguna vencida con saldo; ACTIVO si no; FINALIZADO si el saldo total es cero.

## 6.6. Cancelación total anticipada

```
total a cancelar a la fecha F =
todo lo vencido (capital + interés + IVA + mora pendiente y del tramo)
+ capital pendiente de las cuotas no vencidas
+ interés devengado del período en curso = redondear( saldo × i × días_transcurridos / días_del_período )
+ IVA de ese interés
Intereses de los períodos futuros: 0 (Ley 6624/2020). Penalidad: 0.
```

### Caso BF-09: préstamo BF-01 con la cuota 1 pagada; cancela el 12/11/2026 (10 de 30 días del período 2)

| **Concepto** | **Importe (Gs)** |
|---|---|
| Capital pendiente | 336.642 |
| Interés devengado 336.642 × 2,011776 % × 10/30 | 2.257 |
| IVA | 226 |
| Total a cancelar | 339.125 |
| Ahorro del cliente frente a pagar las cuotas 2 y 3 | 8.728 |

## 6.7. Anticipo parcial a capital

1.  Requiere que no haya cuotas vencidas (si las hay, primero se aplica 6.5).

2.  Del monto entregado se cobra primero el interés devengado del período en curso + IVA (como en 6.6); el resto reduce el capital.

3.  Se genera un **nuevo plan (versión +1)** desde la fecha: se mantiene la cuota C y se reduce la cantidad de cuotas: n' = techo( −ln(1 − S × i / C) / ln(1 + i) ), con S = nuevo saldo. La última cuota se ajusta a saldo cero. El plan anterior queda guardado como histórico.

4.  La primera cuota del plan nuevo conserva su fecha original; su interés se calcula solo por los días restantes del período.

*Ejemplo: préstamo de 5.000.000 Gs a 12 meses (cuota 473.140 Gs). Si el saldo baja a 3.000.000 Gs, quedan 7 cuotas en lugar de las restantes del plan original.*

## 6.8. Métodos del ServicioMotorFinanciero

| **Método** | **Entrada → Salida** |
|---|---|
| cotizar(monto, plazo, fechaPrimerVenc) | → cuota, total con IVA, TEA, TNA, CFT, plan (sin guardar) |
| generarPlanPagos(monto, plazo, tea, fechaPrimerVenc, feriados) | → lista de cuotas (6.2) |
| calcularCapacidadPago(cuotaMax, ingresoCliente, ingresoGarante, cuotaOtrasDeudas) | → % afectación, requiereGarante, habilitado (D-127) |
| validarLimiteTasa(tea, fecha) | → ok / error + límite usado |
| calcularMora(cuota, fecha, parámetros) | → días, moratorio, punitorio, IVA |
| calcularDeudaExigible(préstamo, fecha) | → detalle por cuota y total exigible hoy |
| distribuirPago(préstamo, monto, fecha) | → aplicaciones por concepto, sobrante |
| calcularCancelacionTotal(préstamo, fecha) | → liquidación (6.6) |
| calcularAnticipoParcial(préstamo, monto, fecha) | → liquidación + nuevo plan (6.7) |
| fechaHabil(fecha, feriados) | → siguiente día hábil |

# 7. Estados y transiciones

| **Entidad** | **Estado** | **Entra cuando…** | **Sale hacia…** |
|---|---|---|---|
| Solicitud | BORRADOR | El oficial la crea | PENDIENTE_APROBACION (envía) · CANCELADA (oficial, con motivo) |
| Solicitud | PENDIENTE_APROBACION | Envío completo y válido | APROBADA · RECHAZADA · CANCELADA (gerente) |
| Solicitud | APROBADA | Gerente aprueba | Se formaliza (crea préstamo) · CANCELADA (gerente) |
| Solicitud | RECHAZADA / CANCELADA | Con motivo | Terminal |
| Préstamo | PENDIENTE_DESEMBOLSO | Firma + checklist completo | ACTIVO (desembolso) · ANULADO_ANTES_DESEMBOLSO (gerente) |
| Préstamo | ACTIVO | Desembolso o regularización | EN_MORA · FINALIZADO |
| Préstamo | EN_MORA | Alguna cuota vencida con saldo | ACTIVO · FINALIZADO |
| Préstamo | FINALIZADO | Saldo total = 0 | Terminal (cierre documental aparte) |
| Cuota | PENDIENTE / PARCIAL / PAGADA | Según pagos aplicados | "Vencida" se calcula, no se guarda |
| Caja | ABIERTA | Cajera abre con fondo confirmado | CERRADA (arqueo y rendición) |
| Caja | CERRADA | Cierre confirmado | Terminal e inmutable |
| Usuario | ACTIVA / BLOQUEADA / INACTIVA | Alta · 3 intentos fallidos · baja | Administrador reactiva o inactiva (D-151; antes era el gerente) |
| Reversión | SOLICITADA / AUTORIZADA / RECHAZADA | Cajera solicita · gerente decide | AUTORIZADA ejecuta compensación en caja abierta |

Actualización de EN_MORA: el estado se recalcula (a) al aplicar cualquier pago y (b) al iniciar sesión el primer usuario del día (tarea "actualizarEstadosDelDia"), que revisa las cuotas vencidas desde el día anterior.

# 8. Procesos operativos (flujo del día en el local)

Cada proceso indica quién lo hace, qué debe existir antes, los pasos, lo que valida el sistema y lo que se imprime. Es la base para programar los servicios y para el guion de prueba con el dueño.

| **Momento del día** | **Procesos** |
|---|---|
| Al abrir | PO-15 · PO-06 |
| Durante la atención | PO-01 · PO-02 · PO-03 · PO-04 · PO-05 · PO-07 · PO-08 · PO-09 · PO-10 · PO-14 |
| En horas libres | PO-11 |
| Excepciones | PO-13 |
| Al cerrar | PO-12 |

## PO-01. Atención y cotización rápida

| **Elemento** | **Detalle** |
|---|---|
| Actor | Oficial (también gerente o cajera) |
| Precondiciones | Ninguna. No requiere registrar al interesado. |
| Validaciones del sistema | Monto y plazo dentro de los límites; TEA de la empresa ≤ tope. |
| Resultado e impresos | Cotización impresa (opcional). No se guarda nada. |

**Pasos:**

1.  El interesado pregunta cuánto pagaría por un monto y plazo.

2.  El oficial abre Cotizador (P13), carga monto, plazo y fecha estimada del primer vencimiento.

3.  El sistema muestra cuota con IVA, total a pagar, TEA, CFT y plan.

4.  Si el interesado quiere, se imprime la cotización (sin datos personales).

5.  Si decide pedir el préstamo, el oficial pasa a PO-02 con los mismos valores.

## PO-02. Registro del cliente y expediente

| **Elemento** | **Detalle** |
|---|---|
| Actor | Oficial |
| Precondiciones | Cliente presente con cédula. |
| Validaciones del sistema | C.I. única. No se habilita la consulta sin autorización adjunta (RN-24). Archivos PDF/JPG/PNG ≤ 5 MB. |
| Resultado e impresos | Ficha y expediente del cliente. Autorización de consulta impresa. |

**Pasos:**

1.  Buscar por C.I. Si existe, se reutiliza la ficha (también si antes fue garante).

2.  Cargar o actualizar datos personales, laborales, contacto, ingreso neto y referencias.

3.  Indicar si presenta comprobante de ingresos (si no → requerirá garante, RN-04). **RN-04
    retirada** (18/10/2026, commit `6e7da13` — ver `docs/funcionamiento/RF05_solicitud.md` §10).

4.  Escanear y adjuntar cédula, comprobante de ingresos y de domicilio.

5.  Imprimir la autorización de consulta al buró; el cliente la firma; se escanea y adjunta.

6.  Consultar Informconf (fuera del sistema) y registrar el resultado: fecha, proveedor, resultado (con/sin deudas) y archivo adjunto.

## PO-03. Solicitud y evaluación

| **Elemento** | **Detalle** |
|---|---|
| Actor | Oficial |
| Precondiciones | Cliente registrado sin solicitud abierta ni préstamo no cerrado (RN-01, RN-02). |
| Validaciones del sistema | RN-01/02 al crear y al enviar. Capacidad ≤ límite. Garante distinto del cliente. Consulta al buró registrada para cliente y garante. |
| Resultado e impresos | Solicitud en PENDIENTE_APROBACION. Aparece en la bandeja del gerente. |

**Pasos:**

1.  Crear solicitud: monto, plazo, destino y primer vencimiento (día hábil).

2.  El sistema simula el plan y calcula la capacidad de pago.

3.  Si hace falta garante (RN-04), buscarlo o registrarlo (PO-02 completo, incluida su autorización y consulta). **RN-04 retirada** (18/10/2026, commit `6e7da13` — ver `docs/funcionamiento/RF05_solicitud.md` §10).

4.  Recalcular capacidad con ambos ingresos.

5.  Revisar el checklist de la solicitud y enviar a aprobación.

## PO-04. Aprobación o rechazo

| **Elemento** | **Detalle** |
|---|---|
| Actor | Gerente |
| Precondiciones | Solicitud en PENDIENTE_APROBACION. |
| Validaciones del sistema | RN-06, RN-15, RN-18. Motivo obligatorio al rechazar. |
| Resultado e impresos | APROBADA o RECHAZADA, auditada con usuario y hora. |

**Pasos:**

1.  Abrir la bandeja (P05) y revisar ficha, documentos, resultado del buró, capacidad y plan.

2.  Aprobar: el sistema revalida el tope de tasa y la capacidad, y congela las condiciones.

3.  Rechazar: elegir motivo (Informconf, capacidad, documentación, otro) y detalle.

## PO-05. Formalización y firma

| **Elemento** | **Detalle** |
|---|---|
| Actor | Oficial |
| Precondiciones | Solicitud APROBADA. |
| Validaciones del sistema | Checklist: contrato firmado, pagaré firmado, autorización de consulta, cédulas. Tope de tasa revalidado. |
| Resultado e impresos | Préstamo PENDIENTE_DESEMBOLSO; aparece en "Desembolsos pendientes" y en el total a proveer a caja. |

**Pasos:**

1.  Generar documentos: contrato, pagaré (capital + intereses + IVA), plan de pagos. El sistema asigna el número de operación (CC-AAAA-NNNNN).

2.  Imprimir; cliente y garante firman.

3.  Escanear los documentos firmados y adjuntarlos.

4.  Cuando el checklist está completo, el sistema cambia el préstamo a PENDIENTE_DESEMBOLSO.

## PO-06. Apertura de caja y fondo del día

| **Elemento** | **Detalle** |
|---|---|
| Actor | Cajera y gerente |
| Precondiciones | No hay otra caja ABIERTA. |
| Validaciones del sistema | Una sola caja abierta. El fondo lo confirma el gerente. |
| Resultado e impresos | Caja ABIERTA con saldo inicial trazado. |

**Pasos:**

1.  La cajera abre la caja; el sistema propone el fondo según el total de desembolsos pendientes del día.

2.  El gerente entrega el efectivo y confirma la entrega con su usuario (movimiento ENTREGA_GERENTE).

3.  Durante el día, el gerente puede hacer más entregas si se aprueban nuevos préstamos.

## PO-07. Desembolso

| **Elemento** | **Detalle** |
|---|---|
| Actor | Cajera |
| Precondiciones | Préstamo PENDIENTE_DESEMBOLSO y caja ABIERTA. |
| Validaciones del sistema | Un solo desembolso por préstamo (clave de idempotencia). Efectivo teórico ≥ monto. TEA ≤ tope. |
| Resultado e impresos | Préstamo ACTIVO. Comprobante de desembolso. |

**Pasos:**

1.  Buscar por C.I. o número de operación.

2.  Verificar identidad del cliente y confirmar el monto.

3.  El sistema valida efectivo suficiente y registra la salida.

4.  Imprimir comprobante de desembolso; el cliente lo firma.

## PO-08. Cobro de cuota

| **Elemento** | **Detalle** |
|---|---|
| Actor | Cajera |
| Precondiciones | Préstamo ACTIVO o EN_MORA, caja ABIERTA. |
| Validaciones del sistema | RN-09, RN-10. Doble clic no duplica el cobro. Timbrado vigente. |
| Resultado e impresos | Pago registrado, factura con número correlativo, recibo, estado del préstamo actualizado. |

**Pasos:**

1.  Buscar al cliente. El sistema muestra cuotas, atrasos, mora y total exigible hoy.

2.  Cargar el efectivo recibido.

3.  El sistema muestra la distribución (6.5), el vuelto y el saldo posterior.

4.  Confirmar. Imprimir recibo y factura.

## PO-09. Pago parcial con promesa

| **Elemento** | **Detalle** |
|---|---|
| Actor | Cajera |
| Precondiciones | Igual que PO-08, con monto menor a lo exigible. |
| Validaciones del sistema | RN-20: fecha de promesa obligatoria y futura. |
| Resultado e impresos | Gestión de cobranza tipo PROMESA registrada; aparece en el seguimiento. |

**Pasos:**

1.  El sistema detecta que el pago no cubre lo exigible.

2.  La cajera carga la fecha en que el cliente promete completar (obligatoria) y un comentario.

3.  Se confirma, se imprimen recibo y factura.

## PO-10. Cancelación total o anticipo parcial

| **Elemento** | **Detalle** |
|---|---|
| Actor | Cajera |
| Precondiciones | Préstamo ACTIVO o EN_MORA, caja ABIERTA. |
| Validaciones del sistema | RN-11. Anticipo solo sin cuotas vencidas. |
| Resultado e impresos | Liquidación guardada. Nuevo plan (anticipo) o FINALIZADO (cancelación). |

**Pasos:**

1.  Elegir "Cancelar todo" o "Adelantar capital".

2.  El sistema muestra la liquidación a la fecha (6.6 o 6.7) y el ahorro de intereses.

3.  Cobrar y confirmar.

4.  Cancelación: imprimir constancia de cancelación; el préstamo queda FINALIZADO. Anticipo: imprimir el nuevo plan de pagos.

## PO-11. Seguimiento de morosos

| **Elemento** | **Detalle** |
|---|---|
| Actor | Oficial (y gerente) |
| Precondiciones | Préstamos con cuotas vencidas. |
| Validaciones del sistema | RN-22. La gestión queda con usuario y hora. |
| Resultado e impresos | Historial de gestiones por préstamo; indicador de promesas incumplidas. |

**Pasos:**

1.  Abrir "Seguimiento de mora" (P14): lista ordenada por días de atraso, con teléfono del cliente y del garante.

2.  Llamar y registrar la gestión: tipo (llamada, mensaje, visita al local), resultado, excusa y fecha de promesa si la hay.

3.  Si el préstamo tiene 2 o más cuotas vencidas, el sistema marca "Contactar garante".

4.  Las promesas vencidas sin pago vuelven a aparecer al inicio de la lista.

## PO-12. Cierre de caja y rendición

| **Elemento** | **Detalle** |
|---|---|
| Actor | Cajera y gerente |
| Precondiciones | Caja ABIERTA. |
| Validaciones del sistema | RN-14, RN-23. Diferencia ≠ 0 requiere motivo y revisión del gerente. |
| Resultado e impresos | Arqueo impreso. Caja cerrada e inmutable. |

**Pasos:**

1.  La cajera cuenta el efectivo y lo carga.

2.  El sistema compara con el saldo teórico y muestra la diferencia.

3.  Si hay diferencia, la cajera escribe el motivo (obligatorio).

4.  La cajera registra la rendición del efectivo al gerente (movimiento RENDICION_GERENTE); el gerente confirma con su usuario.

5.  Se imprime el arqueo; la caja queda CERRADA.

## PO-13. Reversión de un cobro o desembolso

> **Fuera del alcance del anteproyecto firmado en la parte de desembolsos** (decisión de Joel,
> 17/10/2026 — ver `docs/base_de_datos/02_analisis_y_problemas.md §8`): el anteproyecto firmado
> (`docs/RF_ANTEPROYECTO.md`) pide RF14 (anular antes del desembolso), RF25 (reversión de un
> cobro) y RNF12 (integridad general) — nunca una reversión de desembolso ya confirmado. El
> desembolso se protege ANTES de confirmarse (RF16: checklist y caja completos; RF17: comprobante
> firmado por el cliente antes de entregar el efectivo). La parte de **cobro** de este proceso
> (RF25) sí está implementada y vigente; la tabla `reversiones_desembolso` que anticipaba la
> parte de desembolso se eliminó del esquema.

| **Elemento** | **Detalle** |
|---|---|
| Actor | Cajera solicita, gerente autoriza |
| Precondiciones | Operación confirmada por error. |
| Validaciones del sistema | RN-13. Nada se borra. Se exige caja ABIERTA para ejecutar. |
| Resultado e impresos | Reversión registrada y vinculada al original. |

**Pasos:**

1.  La cajera busca la operación y solicita la reversión con motivo.

2.  El gerente la ve en su bandeja y autoriza o rechaza.

3.  Si autoriza: se crea el movimiento inverso en la caja abierta actual, se restauran saldos y estados, y se anula la factura asociada (nota de anulación).

## PO-14. Cierre documental

| **Elemento** | **Detalle** |
|---|---|
| Actor | Oficial o gerente |
| Precondiciones | Préstamo FINALIZADO. |
| Validaciones del sistema | Solo préstamos FINALIZADOS. |
| Resultado e impresos | Cierre documental registrado con fecha y usuario. |

**Pasos:**

1.  Buscar el préstamo finalizado.

2.  Imprimir la constancia de devolución del pagaré; el cliente la firma y recibe el pagaré.

3.  Escanear la constancia y marcar el cierre documental.

## PO-15. Inicio del día y control del gerente

| **Elemento** | **Detalle** |
|---|---|
| Actor | Gerente |
| Precondiciones | Sesión iniciada. |
| Validaciones del sistema | — |
| Resultado e impresos | Panel diario. |

**Pasos:**

1.  El sistema actualiza estados del día (EN_MORA).

2.  El panel muestra: cobros de hoy, vencidos, promesas de hoy, desembolsos pendientes y total a proveer, efectivo en caja, cartera en la calle y cobrado de la semana y el mes.

3.  El gerente revisa solicitudes pendientes y reversiones por autorizar.

# 9. Catálogo de requerimientos funcionales

Se mantienen RF-01 a RF-32 del Documento 03 con los ajustes indicados. Se agregan RF-33 a RF-44, que provienen de la segunda entrevista (NR-01 a NR-12 del Acta N.º 2).

## 9.1. Ajustes a requerimientos existentes

| **RF** | **Ajuste** |
|---|---|
| RF-03 | Agrega parámetros fiscales (timbrado, numeración, IVA) y la lista de verificación de apertura. |
| RF-08 | El garante es obligatorio también cuando el cliente no presenta comprobante de ingresos (RN-04). |
| RF-12 | La afectación usa el total de la cuota más alta con IVA. |
| RF-16 | El monto y plazo aprobados son los solicitados (RN-07). |
| RF-18 | El pagaré se emite por capital + intereses + IVA. Número de operación CC-AAAA-NNNNN. |
| RF-21 | Vencimientos en días hábiles (RN-19) e IVA por cuota. |
| RF-23 | Fórmula de mora definida en 6.4 (base capital, tramos, IVA). |
| RF-26 | La caja incluye movimientos ENTREGA_GERENTE y RENDICION_GERENTE; diferencia con motivo (RN-23). |
| RF-28 | Pago parcial exige fecha de promesa (RN-20); excedente como vuelto o anticipo (RN-21). |
| RF-29 | Además del recibo se emite factura (RF-41). |
| RF-31 | Panel diario según PO-15. |

## 9.2. Nuevos requerimientos

| **RF** | **Nombre** | **Criterios de aceptación** | **Módulo** | **Prioridad** |
|---|---|---|---|---|
| RF-33 | Cotizador rápido | Sin datos personales; muestra cuota con IVA, total, TEA, CFT y plan; impresión opcional; no guarda registros. | M03/M06 | Debe |
| RF-34 | Entrega de efectivo del gerente a caja | Movimiento ENTREGA_GERENTE registrado por la cajera y confirmado por el gerente con su usuario; suma al saldo teórico. | M07 | Debe |
| RF-35 | Rendición de caja al gerente | Movimiento RENDICION_GERENTE al cierre, confirmado por el gerente; resta del saldo teórico. | M07 | Debe |
| RF-36 | Total a proveer | Muestra la suma de préstamos PENDIENTE_DESEMBOLSO y el efectivo disponible en caja. | M07/M08 | Debe |
| RF-37 | Gestión de cobranza | Registrar llamada/mensaje/visita, resultado, excusa y promesa; historial por préstamo; promesas vencidas destacadas. | M07/M08 | Debería |
| RF-38 | Alerta de garante | Préstamos con ≥ 2 cuotas vencidas marcados "Contactar garante" con su teléfono. | M08 | Debe |
| RF-39 | Constancias de cierre | Constancia de cancelación y constancia de devolución de pagaré imprimibles y archivadas. | M05 | Debería |
| RF-40 | Feriados y días hábiles | Tabla de feriados administrable; ajuste automático de vencimientos. | M01/M06 | Debe |
| RF-41 | Factura (autoimpresor) | Numeración correlativa sin saltos; datos de timbrado vigentes; capital exento, intereses y mora gravados 10 %; reimpresión como copia; anulación solo por reversión. | M07 | Debe |
| RF-42 | Diferencia de caja | Motivo obligatorio y revisión del gerente; queda en el arqueo. | M07 | Debe |
| RF-43 | Panel diario | Indicadores de PO-15, filtrados por perfil. | M08 | Debe |
| RF-44 | Datos de prueba y lista de apertura | Carga inicial de datos ficticios (sección 16) y lista de verificación antes de operar con clientes reales. | M01 | Debe |

# 10. Modelo de datos

Se mantienen las 22 tablas del Documento 08 y se agregan 5, total 27 tablas. Tipos: montos NUMERIC(18,0) en guaraníes; tasas NUMERIC(9,6) como fracción (0,27 = 27 %); fechas DATE; momentos TIMESTAMP.

## 10.1. Tablas nuevas

| **Tabla** | **Columnas principales** | **Propósito** |
|---|---|---|
| movimientos_caja | id, sesion_caja_id, tipo (ENTREGA_GERENTE, RENDICION_GERENTE, DESEMBOLSO, COBRO, VUELTO, COMPENSACION), monto, signo, referencia_tipo, referencia_id, usuario_registra_id, usuario_confirma_id, fecha_hora, observacion | Libro único de la caja; el saldo teórico es la suma de sus movimientos. |
| gestiones_cobranza | id, prestamo_id, persona_contactada_id, tipo (LLAMADA, MENSAJE, VISITA_LOCAL, PAGO_PARCIAL), resultado, excusa, fecha_promesa, monto_prometido, cumplida (bool), usuario_id, fecha_hora | Seguimiento de morosos y promesas (RF-37). |
| feriados | id, fecha (única), descripcion, activo | Días no hábiles (RF-40). |
| facturas | id, pago_id, timbrado, establecimiento, punto_expedicion, numero (único por timbrado), fecha, cliente_nombre, cliente_documento, total_exenta, total_gravada_10, iva_10, total, estado (EMITIDA, ANULADA), archivo_id, reversion_pago_id | Comprobante fiscal por cada cobro (RF-41). |
| numeradores | clave (FACTURA, OPERACION, RECIBO, SOLICITUD), ultimo_valor, anio | Numeración correlativa con bloqueo de fila (SELECT … FOR UPDATE). |
| departamentos (D-126) | id, nombre (único), activo | Catálogo de los 17 departamentos de Paraguay + Asunción, para el domicilio del cliente. |
| ciudades (D-126) | id, departamento_id, nombre, activo | Catálogo de ciudades/distritos (por departamento); domicilio del cliente pasa a ser por partes. |
| motivos_rechazo_solicitud (D-126) | id, codigo (único), nombre, requiere_detalle (bool), activo, orden | Lista referencial de motivos de rechazo de una solicitud (antes texto libre). |

## 10.2. Columnas agregadas a tablas existentes

| **Tabla** | **Columnas nuevas** |
|---|---|
| personas | presenta_comprobante_ingreso (bool), telefono_alternativo, lugar_trabajo, referencia_nombre, referencia_telefono |
| personas (D-126) | fecha_vencimiento_cedula, conyuge_nombre, conyuge_cedula, conyuge_telefono (obligatorios si estado_civil es Casado/a o Unión de hecho), departamento_id, ciudad_id, barrio, referencia_domicilio, tipo_vivienda (PROPIA/ALQUILADA/FAMILIAR), antiguedad_domicilio_anios, referencia2_nombre, referencia2_telefono |
| personas (D-127) | situacion_laboral (8 códigos), empleador_nombre/ruc/direccion/telefono, cargo, antiguedad_laboral_anios (obligatorios solo si situacion_laboral es ASALARIADO_SECTOR_PRIVADO o FUNCIONARIO_PUBLICO), otros_ingresos_mensuales, gastos_fijos_mensuales (informativos), cuota_otras_deudas_mensual (entra en la fórmula de capacidad de pago, ver D-127 en AVANCE.md — pendiente de confirmar con Joel al cierre de esta sesión) |
| solicitudes_credito (D-126) | motivo_rechazo_id (referencia a motivos_rechazo_solicitud; motivo_rechazo, ya existente, pasa a ser el texto combinado nombre + detalle) |
| cuotas | iva_interes, moratorio_pendiente, punitorio_pendiente, iva_mora_pendiente, fecha_corte_mora, capital_pagado, interes_pagado, estado_pago |
| prestamos | numero_operacion, limite_tasa_id, tea, tasa_mensual, cuota_contractual, fecha_cierre_documental, requiere_contactar_garante (derivado en consulta) |
| sesiones_caja | saldo_teorico_cierre, saldo_fisico, diferencia, motivo_diferencia, gerente_revisa_id |
| pagos | clave_idempotencia (única), efectivo_recibido, vuelto, monto_aplicado, tipo (NORMAL, PARCIAL, CANCELACION, ANTICIPO) |

## 10.3. Tabla cargos_operacion

Se conserva por compatibilidad con el Documento 08, pero en la V1 no se usa: no hay comisiones ni gastos (RN-17). El IVA se guarda en cuotas y aplicaciones.

> **Eliminada del esquema el 17/10/2026** (decisión de Joel, auditoría de toda la base — ver
> `docs/base_de_datos/02_analisis_y_problemas.md §8`): "se conserva por compatibilidad" dejó de
> aplicarse — confirmado con `grep` que ningún código la usaba, y el anteproyecto firmado nunca
> pidió comisiones ni gastos, así que no había ninguna compatibilidad real que mantener.

## 10.4. Reglas de integridad obligatorias

- Índice único parcial: una sola sesión de caja con estado ABIERTA.

- Índice único parcial: un solo desembolso válido (no revertido) por préstamo.

- Índice único parcial por persona: una solicitud abierta y un préstamo no cerrado (además de la validación en el servicio).

- Única (timbrado, establecimiento, punto_expedicion, numero) en facturas.

- Ninguna tabla financiera tiene DELETE desde la aplicación; el usuario de base de la aplicación no tiene permiso DELETE sobre ellas.

- Cobro, desembolso, reversión y cierre de caja se ejecutan en una sola transacción JDBC con su auditoría.

# 11. Documentos imprimibles

Todos se generan con JasperReports a partir de los datos guardados; el PDF emitido se guarda en PostgreSQL (archivos_binarios). Todos llevan encabezado con razón social, RUC, domicilio y teléfono, y pie con usuario que atendió, fecha y hora.

| **Documento** | **Contenido principal** | **Cuándo** |
|---|---|---|
| Cotización | Monto, plazo, cuota con IVA, total, TEA, CFT, plan; leyenda "Cotización informativa válida por 7 días". | PO-01 |
| Autorización de consulta crediticia | Datos del titular, finalidad, proveedor, fecha, firma. | PO-02 |
| Contrato de préstamo | Partes, número de operación, monto, plazo, TEA, TNA, CFT, cuota, vencimientos, mora y punitorio, derecho de pago anticipado, garante, firmas. | PO-05 |
| Pagaré | Número de operación, monto (capital + interés + IVA) en números y letras, vencimiento, lugar, deudor y garante. | PO-05 |
| Plan de pagos | Tabla de 6.2 con total por cuota. | PO-05 y anticipo |
| Comprobante de desembolso | Operación, monto entregado, fecha, cajera, firma del cliente. | PO-07 |
| Factura | Ver 11.1. | PO-08/09/10 |
| Recibo | Operación, efectivo recibido, vuelto, detalle aplicado por cuota y concepto, saldo posterior, próxima cuota. | PO-08/09/10 |
| Constancia de cancelación | Operación, fecha, total pagado, saldo cero. | PO-10 |
| Constancia de devolución de pagaré | Operación, fecha, firma del cliente. | PO-14 |
| Arqueo de caja | Fondo, entregas, desembolsos, cobros, compensaciones, rendición, teórico, físico, diferencia y motivo. | PO-12 |

## 11.1. Contenido de la factura

**Decisión de Joel (26/09/2026, planificación de E7, ver AVANCE.md D-78):** la factura documenta únicamente la prestación del servicio financiero (intereses, moratorio y punitorio, cada uno gravado 10 % con el IVA incluido en el importe); el capital (devolución del préstamo) no es una venta de servicio y no lleva IVA, así que se documenta solo en el recibo, no en la factura — práctica habitual de las financieras en Paraguay. Una línea por concepto y por cuota, con la descripción identificando la operación (ver ejemplo). Si un cobro no tiene ningún concepto gravado (por ejemplo, un anticipo que es solo capital), no se emite factura — solo recibo — y no se consume número de factura.

```
CAPITAL CRÉDITO PARAGUAY S.R.L. RUC 80123456-5
Préstamos personales · Tte. Rojas Silva 1245, Asunción · (021) 555-120
TIMBRADO N.º 16842795 Vigencia 01/09/2026 al 31/08/2027
FACTURA N.º 001-001-0000001 Fecha: 26/10/2026 Condición: CONTADO
Tipo de transacción: Prestación de servicios · Moneda: Guaraníes
Cliente: MARÍA GONZÁLEZ RUC/C.I.: 4.512.337
---
Descripción Gravadas 10 %
Intereses cuota 7 — CC-2026-00014 — vto. 26/10/2026 11.065
Interés moratorio cuota 7 — CC-2026-00014 1.774
Interés punitorio cuota 7 — CC-2026-00014 532
---
Subtotal gravado 13.371
TOTAL A PAGAR: 13.371 (trece mil trescientos setenta y un guaraníes)
Liquidación del IVA (ya incluido en los importes de arriba, no se recalcula): 1.215
```

*Los importes gravados se muestran con IVA incluido, como es habitual en Paraguay; el IVA de la liquidación es el que ya quedó guardado en la base al aplicar el pago, nunca un recálculo (total/11) para evitar diferencias de redondeo. Condición de venta siempre CONTADO. La factura se imprime en original (cliente) y duplicado (archivo). Validar el formato final con el contador antes de operar con clientes reales.*

# 12. Pantallas y navegación

| **Código** | **Pantalla** | **Perfiles** | **Notas** |
|---|---|---|---|
| P01 | Acceso y cambio de contraseña | Todos | Bloqueo al 3.er intento. |
| P02 | Panel del día | Todos (contenido según perfil) | PO-15. |
| P03 | Clientes y expediente | Oficial, gerente; cajera consulta | Búsqueda por C.I., documentos, buró, historial. |
| P04 | Solicitud y evaluación | Oficial | Simulación, capacidad, garante, checklist. |
| P05 | Bandeja de aprobación | Gerente | Aprobar, rechazar, cancelar. |
| P06 | Formalización y documentos | Oficial | Generar, imprimir, adjuntar firmados, cierre documental. |
| P07 | Caja | Cajera; gerente confirma | Apertura, entregas, movimientos, arqueo, rendición. |
| P08 | Desembolsos pendientes | Cajera | Total a proveer. |
| P09 | Cobranza | Cajera | Deuda al día, cobro, parcial, cancelación, anticipo, factura y recibo. |
| P10 | Reversiones | Cajera solicita; gerente autoriza | |
| P11 | Reportes | Gerente | Sección 13. |
| P12 | Usuarios | Administrador (D-151: exclusivo, el gerente perdió el acceso) | Lista de verificación de apertura. |
| P12 | Parámetros, feriados y límites de tasa | Administrador modifica; gerente solo consulta (D-151) | Lista de verificación de apertura. |
| P13 | Cotizador | Todos | RF-33. |
| P14 | Seguimiento de mora | Oficial, gerente | RF-37, RF-38. |

Menú por perfil — Oficial: Panel · Cotizador · Clientes · Solicitudes · Formalización · Seguimiento de mora. Cajera: Panel · Caja · Desembolsos · Cobranza · Reversiones · Cotizador. Gerente: todo, más Aprobaciones, Reportes y Auditoría, salvo Usuarios (D-151: exclusivo del Administrador) y con Parámetros/Configuración solo en consulta (D-151). Administrador (D7): todo, sin excepción.

Estilo: AdminLTE/Bootstrap, textos en español, botones con la acción concreta ("Confirmar cobro"), montos con separador de miles, uso de teclado en caja (Tab/Enter), confirmación con resumen antes de toda operación de dinero.

# 13. Reportes y panel

| **Reporte** | **Contenido** | **Uso** |
|---|---|---|
| Panel del día | Cobros de hoy, vencidos, promesas de hoy, desembolsos pendientes, efectivo en caja, cartera en la calle, cobrado semana/mes | Diario |
| Vencimientos | Cuotas que vencen entre dos fechas, con teléfono | Diario / semanal |
| Morosidad | Préstamos EN_MORA por tramos de atraso (1–30, 31–60, 61–90, 91–180, más de 180), con garante | Diario |
| Gestiones de cobranza | Gestiones y promesas por fecha; promesas incumplidas | Diario |
| Cartera | Préstamos activos, capital en la calle, intereses por cobrar | Mensual |
| Resumen mensual | Préstamos otorgados (cantidad y monto), cobrado (capital, interés, mora, IVA), ganancia (intereses + mora sin IVA), morosidad % | Mensual |
| Caja | Sesiones, movimientos, diferencias, entregas y rendiciones | Diario |
| IVA del mes | Facturas emitidas con gravadas, IVA y exentas (para el contador) | Mensual |
| Incobrables | Préstamos con más de 180 días de atraso | Mensual |
| Estado de cuenta del cliente | Préstamos, pagos, saldo, atrasos, gestiones | A pedido |
| Auditoría | Acciones por usuario, fecha y entidad | Control |
| Cumplimiento (CSV) | Datos estructurados de operaciones y clientes para BCP/SEPRELAD | A pedido |
| Resumen financiero (D-128) | Igual que Resumen mensual pero para un rango desde-hasta arbitrario, con créditos otorgados y en trámite, cobrado desglosado (capital/interés/moratorio/punitorio/IVA), ganancia bruta, cartera, % de mora, demanda por tramo de monto y de plazo (CSV) | A pedido |
| Proyección de fondos (D-128) | Ingresos esperados (cuotas por vencer) y egresos comprometidos (aprobado sin desembolsar) a 30/60/90 días | A pedido |
| Solicitudes / Desembolsos / Cobros / Reversiones / Clientes nuevos / Cierres documentales (D-128) | Listados con filtro desde-hasta, uno por concepto | A pedido |

# 14. Arquitectura, tecnología y requisitos no funcionales

## 14.1. Tecnología (sin cambios respecto del Documento 09)

| **Capa** | **Tecnología** |
|---|---|
| Lenguaje / servidor | Java 8, Servlets/JSP (javax), Apache Tomcat 9, empaquetado WAR con Maven |
| Base de datos | PostgreSQL con JDBC 42.7.1; conexión administrada por la aplicación (bd.properties dentro del .war, ver 14.4) |
| Arquitectura | MVC: JSP (vista) → Servlet (controlador) → Servicio (negocio) → DAO (SQL) |
| Documentos | JasperReports 6.20.6 (plantillas .jrxml en el WAR; PDF emitidos en PostgreSQL) |
| Seguridad | jBCrypt 0.4 para contraseñas; filtros de autenticación y autorización |
| Interfaz | AdminLTE 3, Bootstrap 4, jQuery, SweetAlert2, Font Awesome |
| Pruebas | JUnit 4.13.2 (motor financiero con casos BF del Anexo C) |

## 14.2. Ajustes a los requisitos no funcionales

Corrección (pedido de Joel): la numeración de esta tabla no coincidía con la del anteproyecto
entregado y firmado (`ANTE_PROYECTO_FINAL_JOEL_ACOSTA_v7.docx` — ver también
`docs/RF_ANTEPROYECTO.md`, que ya tiene los 18 RNF copiados textualmente). El anteproyecto firmado
manda; se corrigen los números de acá.

| **RNF** | **Valor V1** |
|---|---|
| RNF-14 Rendimiento | Búsquedas ≤ 2 s; cobro ≤ 3 s; documento Jasper ≤ 4 s (el anteproyecto pide <3 s en general; esto lo detalla por tipo de operación). |
| Concurrencia (sin número propio en el anteproyecto — deriva de RNF-02, "cuatro usuarios") | Hasta 5 usuarios simultáneos (antes 10). Equipo: 1 servidor (la PC del gerente o la de caja) + 2 PC en red local. |
| RNF-09 Seguridad (bloqueo e intentos fallidos) | El sistema bloquea la cuenta después de `seguridad.intentos_login` intentos fallidos (3 por defecto) y muestra el mismo mensaje sin revelar si el usuario existe — ver `ServicioAutenticacion.mensajeAccesoRechazado`. Un bloqueo automático se libera solo a los `seguridad.minutos_bloqueo` minutos (15 por defecto); uno manual (administrador, ABM Usuarios) nunca se libera solo. |
| RNF-16 Respaldo | Script de respaldo diario (pg_dump) ejecutado por el gerente al cerrar, con copia en pendrive o disco externo. Prueba de restauración mensual. |
| RNF-03 Software del servidor (despliegue) | Entrega: sgcpc.war + copia de la base. Desarrollo en Windows 11; defensa en Ubuntu Server con Tomcat 9. Sin necesidad de internet (ver 14.4). |
| RNF-12 Integridad (conservación) | Mínimo 5 años, sin borrado (RN-26). |

## 14.3. Idempotencia de operaciones de dinero

Cada formulario de cobro o desembolso lleva una clave única (UUID) generada al abrir la pantalla. El servicio rechaza una segunda operación con la misma clave y devuelve la ya registrada. Así, un doble clic o un recargar la página nunca duplica dinero.

## 14.4. Portabilidad: todo viaja en el .war y en la base de datos

> **Regla de entrega**
> La defensa se hace en el servidor del tutor (Ubuntu Server + Tomcat 9 + PostgreSQL). Se entregan **solo dos cosas**: el archivo **sgcpc.war** y la **copia de la base de datos** (backup de pgAdmin + script SQL plano). No se instala ni se configura nada más en ese servidor.

| **Elemento** | **Dónde vive** |
|---|---|
| Código, JSP, CSS, JS, AdminLTE, íconos | Dentro del .war (sin CDN: el servidor puede no tener internet) |
| Librerías (driver PostgreSQL, JasperReports, jBCrypt, etc.) | WEB-INF/lib del .war; nada en la carpeta lib del Tomcat |
| Plantillas de reportes (.jrxml y .jasper) | src/main/resources/reportes/ → dentro del .war |
| Fuentes de los PDF | Extensión de fuentes de JasperReports dentro del .war (DejaVu Sans) |
| Archivos subidos (cédulas, firmados, autorizaciones) | PostgreSQL, tabla archivos_binarios (BYTEA) |
| PDF emitidos (facturas, recibos, contratos) | PostgreSQL, tabla archivos_binarios (BYTEA) |
| Parámetros, usuarios, feriados, datos de prueba | PostgreSQL (viajan en la copia de la base) |
| Conexión a la base | Archivo WEB-INF/classes/configuracion/bd.properties dentro del .war (url, usuario, contraseña). Reemplaza al DataSource JNDI del Documento 09, para no tocar el Tomcat del tutor. |

### Reglas por ser Ubuntu el servidor final

- Nombres de archivos y carpetas en minúscula y sin espacios ni tildes (Linux distingue mayúsculas).

- Rutas con "/" y siempre relativas al classpath; nunca rutas de Windows (C:\\..).

- JasperReports en modo sin pantalla: la aplicación fija java.awt.headless=true al arrancar (ServletContextListener).

- Codificación UTF-8 en todo: archivos fuente, JSP, base de datos y respuestas HTTP.

- Compilación para Java 8 (source/target 1.8); el .war corre también si el servidor tiene Java 11 o 17.

- La base se entrega de dos formas: backup de pgAdmin (formato custom) y script SQL plano, por si las versiones de PostgreSQL difieren.

- **Primer ingreso al gerente en el servidor del tutor (D-122, AVANCE.md)**: la contraseña de `gerente`/`oficial`/`cajera` que trae la base entregada es el hash BCrypt fijo del seed (`bd/03_parametros.sql`) -- su valor en texto plano nunca se documentó en ningún archivo (regla de D-45/D-118: una contraseña nunca se guarda ni se imprime en texto plano). Por eso, apenas se restaura la base en el servidor nuevo, hay que correr `bd/desbloquear_usuario.sh gerente --restablecer --motivo "..."` (con `bd.properties` apuntando a esa base) para obtener una contraseña temporal recién generada y poder entrar por primera vez; el sistema exige cambiarla en ese primer acceso. Repetir para `oficial`/`cajera` si hace falta entrar con esos perfiles antes de que el gerente les restablezca la suya desde la pantalla de Usuarios.

## 14.5. Reportes con JasperReports: forma de trabajo

- Todo lo que se imprime o se exporta a PDF (factura, recibo, contrato, pagaré, plan, constancias, arqueo, reportes) se hace con JasperReports 6.20.6.

- Cada reporte tiene **dos archivos**: el .jrxml (diseño editable en Jaspersoft Studio) y el .jasper (compilado). Ambos van en src/main/resources/reportes/ y se compilan con la misma versión 6.20.x.

- **Antes de hacer cualquier reporte, se pregunta al analista** qué debe mostrar y cómo. No se diseña un reporte por cuenta propia.

- El reporte se entrega como .jrxml completo (diseño, parámetros, consulta o campos, variables). El analista lo revisa en Jaspersoft Studio (vistas Design, Source y Preview) y confirma.

- Los reportes no calculan importes financieros: muestran lo que el motor financiero ya guardó. La forma de pasar los datos (consulta SQL dentro del .jrxml o datos enviados desde Java) se decide con el analista en cada reporte.

- Cada PDF emitido se guarda en PostgreSQL; si se pide de nuevo, se entrega el guardado (copia), no se regenera con otros datos.

## 14.6. Entorno y herramientas de trabajo

| **Uso** | **Herramienta** |
|---|---|
| Desarrollo | Windows 11 (laptop del analista): NetBeans, VS Code, Git, Maven, Tomcat 9, PostgreSQL + pgAdmin, Jaspersoft Studio |
| Defensa | Servidor Ubuntu Server del tutor: Tomcat 9 + PostgreSQL |

# 15. Estructura del proyecto y convenciones

```
sgcpc/
├── pom.xml
├── docs/ (especificación, reglas de desarrollo y referencias en Markdown)
├── bd/ 01_esquema.sql · 02_indices.sql · 03_parametros.sql · 04_datos_prueba.sql · respaldo.bat
└── src/main/java/py/com/capitalcredito/sgcpc/
├── controladores/ (Servlets: ControladorCobranzasServlet, …)
├── servicios/ (ServicioMotorFinanciero, ServicioCobranza, ServicioCaja, …)
├── dao/ (PrestamoDAO, CuotaDAO, MovimientoCajaDAO, …)
├── modelos/ dto/ enumeraciones/ filtros/ infraestructura/ excepciones/
src/main/resources/reportes/ (factura.jrxml + factura.jasper, recibo.jrxml + recibo.jasper, …)
src/main/resources/configuracion/bd.properties · fuentes/ (extensión de fuentes Jasper)
src/main/webapp/WEB-INF/vistas/ (JSP por pantalla) · recursos/ (css, js, adminlte)
src/test/java/…/servicios/ServicioMotorFinancieroTest.java
```

- Nombres del dominio en español y descriptivos; el mismo concepto con el mismo nombre en tabla, clase, pantalla y reporte.

- Controladores sin SQL ni cálculos; JSP sin lógica; DAO sin decisiones de negocio.

- Transacciones abiertas y cerradas en el servicio (una Connection compartida por la operación).

- Errores de negocio con NegocioException y mensaje claro para el usuario; errores técnicos al log, nunca a la pantalla.

- Git: una rama por etapa del plan (sección 17); commit al terminar cada criterio de "terminado".

# 16. Datos de prueba

## 16.1. Usuarios

| **Usuario** | **Perfil** | **Nombre (ficticio)** | **Contraseña inicial** |
|---|---|---|---|
| gerente | GERENTE | Ramón Benítez | Cambiar@2026 (exige cambio al primer ingreso) |
| oficial | OFICIAL_CREDITO | Laura Ortiz | Cambiar@2026 |
| cajera | CAJERO | Sofía Martínez | Cambiar@2026 |

## 16.2. Escenarios de préstamos

Las fechas se calculan relativas al día de carga (H = hoy) en el script 04_datos_prueba.sql, para que en cualquier fecha de la demostración los atrasos estén vigentes. Todas las personas son ficticias.

| **N.º** | **Cliente ficticio** | **Situación** | **Cómo se genera** | **Demuestra** |
|---|---|---|---|---|
| E1 | María González | Al día | Desembolso H−35; cuota 1 pagada a tiempo | Cobro normal, factura y recibo |
| E2 | Carlos Benítez | 15 días de atraso | Cuota 1 venció H−15, sin pago | Moratorio, punitorio e IVA |
| E3 | Ana Duarte (garante: Pedro Duarte) | 2 cuotas vencidas | Cuotas 1 y 2 vencidas (H−45 y H−15) | Orden de aplicación y alerta de garante |
| E4 | Jorge Villalba | Más de 180 días | Cuotas vencidas desde H−200 | Reporte de incobrables y morosidad por tramos |
| E5 | Rosa Cáceres | Pago parcial con promesa | Pagó 50 % de la cuota vencida en H−5; promesa en H+2 | Pago parcial y seguimiento |
| E6 | Luis Aquino | Cancelado anticipadamente | Canceló en H−20 al día 10 del período | Liquidación sin intereses futuros, constancia |
| E7 | Elena Rojas | Finalizado | Pagó todas las cuotas; pagaré aún no devuelto | Nueva solicitud permitida y cierre documental |
| E8 | Miguel Sosa | Solicitud pendiente | PENDIENTE_APROBACION creada H−1 | Bandeja del gerente |
| E9 | Patricia Giménez | Aprobado, sin desembolsar | PENDIENTE_DESEMBOLSO, firmado H | Total a proveer y desembolso |
| E10 | Diego Fernández | Rechazado por Informconf | RECHAZADA con motivo | Registro de consulta y motivo |
| E11 | Nuevo interesado | Sin registrar | — | Cotizador y alta completa en vivo |

Se incluyen además: feriados nacionales 2026 y 2027, el límite de tasa vigente, una caja cerrada de H−1 con arqueo y una reversión de cobro ya autorizada, para mostrar el historial.

## 16.3. Lista de verificación antes de atender clientes reales

1.  Cargar razón social, RUC, domicilio y representante legal reales.

2.  Cargar el timbrado real (número, vigencia, establecimiento, punto de expedición) y reiniciar la numeración de facturas.

3.  Cargar registro BCP y SIRO reales.

4.  Cargar el tope de tasa oficial del BCP del mes y la TEA de la empresa.

5.  Confirmar con el contador el IVA y el formato de la factura.

6.  Reemplazar los modelos de contrato y pagaré por los revisados por un abogado.

7.  Ejecutar el script de limpieza de datos de prueba y crear los usuarios reales.

8.  Hacer un respaldo y una prueba de restauración.

# 17. Plan de desarrollo paso a paso

Orden pensado para tener cuanto antes algo que el dueño pueda ver y para construir primero lo que más riesgo tiene (cálculos y dinero). Cada etapa termina con su criterio de "terminado"; no se pasa a la siguiente sin cumplirlo.

| **Etapa** | **Qué se construye** | **Terminado cuando…** | **Días** |
|---|---|---|---|
| E0 Base | Proyecto Maven/WAR, web.xml, DataSource, filtros, plantilla AdminLTE, scripts 01–03 de base de datos | El WAR despliega en Tomcat y muestra la pantalla de acceso | 1 |
| E1 Motor financiero | ServicioMotorFinanciero completo + pruebas JUnit BF-01 a BF-10 | Todas las pruebas del Anexo C pasan | 2 |
| E2 Seguridad y parámetros | Login, perfiles, usuarios, parámetros, feriados, límites de tasa, auditoría, P01/P02/P12 | Los 3 usuarios entran y ven solo su menú; bloqueo al 3.er intento | 1 |
| E3 Clientes y cotizador | P03 y P13: ficha, documentos (BYTEA), autorización, consulta al buró, cotizador | Se registra un cliente con documentos y se cotiza sin registrar | 1 |
| E4 Solicitud y aprobación | P04 y P05: solicitud, capacidad, garante, bandeja, aprobar/rechazar | Flujo E8 y E10 funcionan; reglas RN-01/02/03/04/06/15 verificadas | 1 |
| E5 Formalización | P06: contrato, pagaré, plan (Jasper), firmados, checklist | Préstamo pasa a PENDIENTE_DESEMBOLSO con PDFs guardados | 1 |
| E6 Caja y desembolso | P07 y P08: apertura, entregas, desembolso, comprobante, arqueo, rendición | Caja cuadra; doble clic no duplica; caja cerrada no se modifica | 1 |
| E7 Cobranza | P09: deuda al día, cobro, parcial con promesa, factura, recibo, cancelación, anticipo | Escenarios E1, E2, E3, E5, E6 dan los importes esperados | 2 |
| E8 Mora y reportes | P14 y P11: seguimiento, alerta de garante, panel, reportes | Panel y reportes coinciden con los datos de prueba | 1 |
| E9 Reversiones y cierre documental | P10 y PO-14 | Reversión con caja cerrada compensa en caja abierta | 1 |
| E10 Datos de prueba e instalación | Script 04, respaldo.bat, manual corto de uso, instalación en la PC del cliente | Guion de prueba (18.2) completo sin errores | 1 |

*Total estimado: 13 días de trabajo. Si el tiempo aprieta, E9 y parte de E8 (reportes mensuales) pueden quedar para después de la prueba en el local sin afectar la operación diaria.*

# 18. Pruebas y aceptación

## 18.1. Casos financieros de referencia (Anexo C)

Se programan como pruebas JUnit antes de las pantallas. Si un caso no da el resultado esperado, el defecto es crítico aunque todo lo demás funcione.

## 18.2. Guion de prueba en el local con el dueño

1.  La cajera abre la caja; el gerente entrega el fondo que propone el sistema.

2.  Un interesado pide cotización de 2.000.000 Gs a 6 meses (cotizador).

3.  El oficial registra al nuevo cliente (E11), carga documentos y autorización, registra la consulta al buró.

4.  Crea la solicitud; el sistema exige garante porque el cliente no tiene comprobante de ingresos; se registra el garante.

5.  El gerente aprueba. El oficial imprime contrato, pagaré y plan, adjunta los firmados.

6.  La cajera desembolsa e imprime el comprobante.

7.  La cajera cobra a E1 (al día), a E2 (con mora) y a E3 (2 cuotas vencidas, pago que no alcanza para todo).

8.  E5 paga una parte y deja promesa; se ve en el seguimiento.

9.  Un cliente cancela todo antes de tiempo: se muestra el ahorro de intereses y la constancia.

10. El oficial abre el seguimiento de mora, llama a E3 y ve la alerta de contactar al garante.

11. La cajera registra un cobro por error y solicita la reversión; el gerente la autoriza.

12. Cierre: arqueo, diferencia (si la hay) con motivo, rendición al gerente, caja cerrada.

13. El gerente revisa el panel y el resumen del día; se hace el respaldo.

## 18.3. Criterio de aceptación

El dueño firma la aceptación cuando el guion 18.2 se completa sin errores críticos y los importes coinciden con el simulador de tasas (Doc. 14).

# 19. Riesgos, supuestos y pendientes

| **N.º** | **Riesgo / pendiente** | **Impacto** | **Acción** |
|---|---|---|---|
| R-01 | Tope de tasa usado (27,39 %) no verificado contra la publicación oficial | Tasa ilegal si es menor | Verificar en bcp.gov.py antes de operar; es parámetro |
| R-02 | IVA sobre intereses y formato de factura no confirmados por contador | Factura incorrecta | Parámetros; validar con contador (16.3) |
| R-03 | Obligación de SIFEN a corto plazo | Factura autoimpresor quedaría obsoleta | Modelo de factura aislado; SIFEN en V2 |
| R-04 | Contratos sin revisión legal | Documentos no ejecutables | Plantillas reemplazables; revisión de abogado |
| R-05 | Límite de afectación: 40 % vs 20 % | — | CERRADO: el cliente acepta 40 % (D-05) |
| R-06 | Plazo corto para desarrollar | Funciones incompletas | Prioridades MoSCoW (2.3) y orden del plan (17) |
| R-07 | Pérdida de datos en servidor local | Pérdida de cartera | Respaldo diario externo y prueba de restauración |
| R-08 | Uso de datos ficticios con clientes reales | Problemas con la DNIT | Lista de verificación 16.3 obligatoria |

## 19.1. Registro de decisiones

*Toda decisión nueva durante el desarrollo se anota aquí antes de programarla. Las decisiones D-01 a D-09 quedan cerradas.*

| **N.º** | **Fecha** | **Situación** | **Decisión** | **Quién decide** |
|---|---|---|---|---|
| D-01 | 23/09/2026 | Datos institucionales reales no disponibles | Usar datos ficticios realistas y parametrizables | Analista y cliente |
| D-02 | 23/09/2026 | Excedente de pago ("vuelto para la próxima cuota") | Vuelto o anticipo a capital; no se aplica a la próxima cuota, para no cobrar interés futuro (RN-21) | Aprobado por el cliente |
| D-03 | 23/09/2026 | Cancelación con todo el interés pedida por el cliente | No se permite (Ley 6624/2020) | Ley |
| D-04 | 23/09/2026 | Nivel de interés moratorio y punitorio | Moratorio = tasa del préstamo; punitorio = 30 % del moratorio (máximos legales, parametrizables) | Aprobado por el cliente |
| D-05 | 23/09/2026 | Límite de la cuota sobre el ingreso (40 % o 20 %) | 40 % del ingreso neto comprobado, parametrizable | Aprobado por el cliente |
| D-06 | 23/09/2026 | Dónde corre la defensa | Solo sgcpc.war + copia de la base en el servidor Ubuntu del tutor; nada fuera de ellos (14.4) | Tutor / analista |
| D-07 | 23/09/2026 | Conexión a la base | bd.properties dentro del .war en lugar de DataSource JNDI del Tomcat | Analista |
| D-08 | 23/09/2026 | Reportes | JasperReports con .jrxml + .jasper; se consulta al analista antes de cada reporte (14.5) | Analista |
| D-09 | 23/09/2026 | Herramienta de programación | NetBeans en la PC del analista | Analista |
| D-10 | | | | |
| D-11 | | | | |

# Anexo A. Cambios respecto de los Documentos 01 a 11

| **Documento** | **Cambio** |
|---|---|
| 01 Marco institucional | Datos institucionales ficticios; parámetros fiscales; RN-15 a RN-27; tope y TEA como parámetros |
| 02 Estructura modular | Gastos del local fuera de alcance; cotizador, gestión de cobranza y movimientos del gerente dentro de M03/M07/M08 |
| 03 Requisitos funcionales | Ajustes 9.1 y RF-33 a RF-44 |
| 04 Proceso TO-BE | Procesos PO-01 a PO-15 (sección 8) |
| 05 Casos de uso | Nuevos CU: Cotizar, Entregar/rendir efectivo, Registrar gestión de cobranza, Emitir factura, Administrar feriados |
| 06 RNF | Concurrencia (5 usuarios), RNF-16 (respaldo por el gerente), RNF-12 (5 años) |
| 07 Trazabilidad | Agregar NB-09 (cotización), NB-10 (seguimiento de mora), NB-11 (facturación) y los RF nuevos |
| 08 Modelo de datos | 27 tablas (sección 10) |
| 09 Arquitectura | Servicios nuevos: ServicioCotizacion, ServicioGestionCobranza, ServicioFacturacion; DAO correspondientes. Conexión por bd.properties en lugar de JNDI (D-07). Portabilidad y reportes (14.4 y 14.5) |
| 10 Interfaces | P13 Cotizador y P14 Seguimiento de mora; nuevos documentos imprimibles (sección 11) |
| 11 Pruebas | Casos BF del Anexo C, escenarios E1–E11 y guion 18.2 |

# Anexo B. Glosario

| **Término** | **Significado** |
|---|---|
| TEA | Tasa efectiva anual: lo que realmente cuesta el préstamo en un año. |
| TNA | Tasa nominal anual: tasa mensual × 12 (informativa). |
| CFT | Costo financiero total: incluye intereses e IVA, expresado como tasa anual. |
| Sistema francés | Cuotas iguales; al principio se paga más interés y al final más capital. |
| Moratorio | Interés por atraso sobre el capital vencido. |
| Punitorio | Recargo adicional por atraso, como máximo 30 % del moratorio. |
| Timbrado | Autorización de la DNIT para emitir facturas con una numeración y vigencia. |
| OCD | Otorgante de Crédito Dinerario: casa de crédito registrada en el BCP. |
| Idempotencia | Una operación repetida por error produce un solo efecto. |

# Anexo C. Casos financieros de referencia

| **Código** | **Caso** | **Resultado esperado** |
|---|---|---|
| BF-01 | 500.000 Gs, 3 meses, TEA 27 % | Cuota 173.417; intereses 10.059 / 6.772 / 3.420; total con IVA 522.276 (tabla 6.2) |
| BF-02 | Redondeo | Saldo final exactamente 0; última cuota ajustada |
| BF-03 | Ingreso 3.000.000, cuota con IVA 174.423 | Afectación 5,8 % → habilitado |
| BF-04 | Ingreso 400.000, cuota con IVA 174.423 | Afectación 43,6 % → exige garante |
| BF-05 | TEA 38,5 % con tope 27,39 % | Bloqueo de aprobación |
| BF-06 | Cuota 1 del BF-01 pagada 15 días tarde | Moratorio 1.613; punitorio 484; IVA 161 + 48; total 176.729 |
| BF-07 | BF-06 con pago de 100.000 | Aplica 1.774 + 532 + 11.065 + 86.629 a capital; cuota 1 PARCIAL |
| BF-08 | 5.000.000 a 12 meses, saldo baja a 3.000.000 | Cuota se mantiene 473.140; quedan 7 cuotas |
| BF-09 | Cancelación total del BF-01 el 12/11/2026 | 339.125 (capital 336.642 + interés 2.257 + IVA 226) |
| BF-10 | Vencimiento en sábado 02/01/2027 | Se mueve al lunes 04/01/2027 |
