> **Documento de referencia (versión FINAL original).** Donde difiera de `00_especificacion_maestra.md`, prevalece el Documento 00. En particular: el modelo de datos v1 tiene 27 tablas (ver Doc. 00 §10), la conexión es por `bd.properties` y no por JNDI (Doc. 00 §14.4), y se agregan las pantallas P13 y P14 (Doc. 00 §12).

**SISTEMA DE GESTIÓN DE CRÉDITOS  
PERSONALES Y COBRANZAS**

**DOCUMENTO 10**

**DISEÑO DE INTERFACES DE USUARIO  
Y ESPECIFICACIÓN DE REPORTES**

Capital Crédito Paraguay S.R.L.

**Versión FINAL para desarrollo - V1**

> **Objetivo**
> Definir cómo debe presentarse y comportarse la interfaz del SGCPC para un único local y pocos usuarios, manteniendo correspondencia con los módulos M01 a M08, los casos de uso CU-01 a CU-12, el modelo PostgreSQL del Documento 08 y la arquitectura MVC del Documento 09. Este documento fija comportamiento, información, permisos y salidas; la estética puede mejorarse sin alterar esas reglas.

| **Ámbito** | **Definición** |
|---|---|
| Frontend | JSP + AdminLTE + Bootstrap + jQuery/jQuery UI + Font Awesome + SweetAlert2 + Tablesort + Source Sans Pro. |
| Arquitectura visual | Vistas JSP servidas por Controladores Servlet; las JSP no acceden a PostgreSQL ni contienen reglas financieras. |
| Documentos imprimibles | JasperReports 6.20.6 es obligatorio para todo documento imprimible/PDF formal. |
| Persistencia documental | Los archivos históricos y adjuntos que deban conservarse se almacenan dentro de PostgreSQL; no en carpetas locales. |
| Operación monetaria | Exclusivamente efectivo; toda operación requiere una sesión de caja ABIERTA. |

# 10.1. Principios de diseño y libertad de mejora UI/UX

- Simplicidad operativa: cada pantalla mostrará únicamente la información y acciones necesarias para la tarea actual.

- Navegación predecible por perfil: el usuario verá solo opciones acordes a su responsabilidad, sin que la ocultación visual sustituya la autorización del servidor.

- Terminología oficial: campos, estados, botones, mensajes y reportes usarán los mismos conceptos definidos en los Documentos 01 a 09.

- Cálculo centralizado: la interfaz no calculará cuotas, mora, punitorio, TEA, CFT, liquidaciones ni distribución de pagos; mostrará resultados de ServicioMotorFinanciero.

- Operaciones monetarias controladas: desembolso, cobro, cierre de caja y reversión mostrarán un resumen previo y requerirán confirmación explícita.

- Trazabilidad visible: en operaciones críticas se mostrará el identificador correspondiente, estado, fecha/hora y usuario cuando sea útil para el operador.

- Diseño eficiente para teclado: en caja y carga repetitiva se facilitará Tab y Enter cuando no comprometa la seguridad de una confirmación sensible.

- Diseño visual profesional: un especialista UI/UX podrá mejorar jerarquía, disposición, espaciado, componentes, iconografía y estética sin alterar datos obligatorios, permisos, reglas, estados ni secuencia funcional.

> **Criterio de flexibilidad de interfaz**
> Los bocetos y distribuciones de este documento son referencias funcionales, no especificaciones pixel-perfect. Cloud Code o un especialista de diseño puede mejorar la presentación, pero no puede eliminar datos requeridos, cambiar autorizaciones, inventar acciones, modificar reglas de negocio ni alterar el flujo aprobado.

# 10.2. Estándar visual y técnico del frontend

| **Componente** | **Uso autorizado** |
|---|---|
| AdminLTE | Estructura general de panel, menú lateral, tarjetas, cajas informativas y distribución del escritorio. |
| Bootstrap | Grid, formularios, botones, componentes responsivos y espaciado. |
| jQuery / jQuery UI | Interacción de formularios y componentes existentes cuando sea necesario; no contiene reglas financieras. |
| Font Awesome | Iconografía consistente para acciones y estados. |
| SweetAlert2 | Confirmaciones sensibles, alertas y mensajes de éxito/error de alto nivel. |
| Tablesort | Ordenamiento visual de tablas cuando aplique. El servidor sigue siendo responsable de filtros y datos. |
| Source Sans Pro | Tipografía principal de la interfaz, coherente con AdminLTE. |

- Las versiones concretas de librerías deberán permanecer compatibles y congeladas en el proyecto; no se mezclarán generaciones incompatibles de Bootstrap/AdminLTE.

- Las JSP residirán bajo WEB-INF/vistas o ubicación protegida equivalente y serán alcanzadas mediante Controladores Servlet.

- JavaScript podrá validar experiencia de usuario y formato, pero toda regla crítica se revalida en el servidor.

- No se incorporará otro framework frontend ni otro motor PDF sin aprobación documental.

# 10.3. Navegación principal por perfil

| **Perfil** | **Menú funcional visible** | **Límite principal** |
|---|---|---|
| Oficial de Crédito | Inicio; Clientes y expediente; Nueva solicitud; Solicitudes; Formalización/documentos. | No aprueba, no mueve caja y no autoriza reversiones. |
| Gerente/Administrador | Inicio; Aprobaciones; Clientes/consultas; Usuarios; Parámetros; Caja/operaciones en modo control; Reversiones; Reportes; Auditoría. | Aprueba/rechaza/cancela según reglas, administra y autoriza reversiones; no sustituye al Cajero en la operatoria normal. |
| Cajero | Inicio; Caja; Desembolsos pendientes; Cobranza; Reversiones solicitables; Recibos/comprobantes. | No modifica condiciones aprobadas, no aprueba solicitudes y no autoriza por sí solo una reversión. |

# 10.4. Catálogo oficial de pantallas

| **Código** | **Pantalla** | **Perfil principal** | **Propósito** |
|---|---|---|---|
| P01 | Acceso y credenciales | Todos | Inicio/cierre de sesión, mensajes de bloqueo y cambio de contraseña propia. |
| P02 | Inicio / Panel personal | Según perfil | Accesos rápidos y pendientes relevantes para el rol; no es un sistema de notificaciones externas. |
| P03 | Clientes, expediente y consulta crediticia | Oficial; Gerencia consulta | Ficha única, documentos, consentimiento y consulta crediticia. |
| P04 | Solicitud y evaluación crediticia | Oficial | BORRADOR, simulación, capacidad, validaciones y envío a aprobación. |
| P05 | Aprobación de solicitud | Gerente/Administrador | Revisión, condiciones definitivas, aprobar, rechazar o cancelar cuando corresponda. |
| P06 | Formalización y documentos | Oficial; Gerencia control | Plan definitivo, Jasper, checklist, firmas, PENDIENTE_DESEMBOLSO y cierre documental autorizado. |
| P07 | Caja | Cajero; Gerencia consulta | Apertura única, saldo teórico, movimientos, arqueo y cierre inmutable. |
| P08 | Desembolso | Cajero | Entrega única en efectivo, validación de caja disponible y comprobante. |
| P09 | Cobranza y pagos anticipados | Cajero | Cobro normal/parcial, mora, vuelto, anticipo, cancelación total y recibo. |
| P10 | Reversiones monetarias | Cajero + Gerencia | Reversión autorizada de cobro o desembolso; original intacto y movimiento compensatorio. |
| P11 | Reportes, cartera y control | Gerente/Administrador | Indicadores, filtros, JasperReports y exportaciones de cumplimiento. |
| P12 | Usuarios y parámetros | Gerente/Administrador | Usuarios, bloqueo/reactivación/restablecimiento y parámetros institucionales/financieros autorizados. |

> **Nota de diseño**
> Los módulos M01 a M08 son límites lógicos del sistema; no deben convertirse obligatoriamente en ocho menús separados. La navegación puede agruparse de manera natural siempre que preserve permisos, trazabilidad y responsabilidades.

# 10.5. P01 y P02 - Acceso, credenciales e inicio

## P01 - Acceso y credenciales

- Solicita nombre de usuario y contraseña; no revela cuál dato fue incorrecto.

- Al tercer intento fallido consecutivo la cuenta queda BLOQUEADA según RF/RNF vigentes.

- Un usuario autenticado puede cambiar su propia contraseña. Si Gerencia restablece una contraseña, el sistema exigirá cambio en el siguiente acceso.

- La pantalla no expone detalles técnicos, SQL, rutas, stacktraces ni información de PostgreSQL.

## P02 - Inicio / Panel personal

- Muestra accesos rápidos y pendientes del perfil: solicitudes a completar, aprobaciones pendientes, desembolsos habilitados, caja abierta/cerrada, etc.

- Las “alertas” de P02 son indicadores internos del sistema; no implican correo, SMS, push ni mensajería automática en la V1.

# 10.6. P03 - Clientes, expediente y consulta crediticia

| **Bloque** | **Contenido / comportamiento** |
|---|---|
| Ficha | C.I., nombres, apellidos, nacimiento, estado civil, domicilio, teléfono/WhatsApp, ingreso comprobado y demás datos definidos. |
| Expediente | Lista de documentos con tipo, fecha, estado, usuario y acciones autorizadas de ver/descargar/cargar versión. |
| Archivos | PDF/JPG/PNG validados; el archivo se guarda en PostgreSQL y se recupera mediante ControladorDocumentosServlet. Nunca se muestra una ruta local. |
| Consentimiento | Debe existir evidencia previa antes de registrar como válida una consulta crediticia. |
| Buró/proveedor | Se muestra el resultado realmente recibido. No se inventan categorías como “riesgo bajo” si el proveedor no las entregó. |

# 10.7. P04 - Solicitud y evaluación crediticia

> **Responsabilidad**
> Pantalla del Oficial de Crédito. Prepara y evalúa preliminarmente; no aprueba ni rechaza en nombre de Gerencia.

| **Bloque** | **Contenido / comportamiento** |
|---|---|
| Cliente | C.I., nombre, ingreso evaluado, estado de habilitación y resumen de expediente. |
| Condiciones solicitadas | Monto solicitado, plazo, destino, tasa propuesta y primera fecha de vencimiento. |
| Simulación M06 | Cuota estimada, TNA, TEA, CFT cuando corresponda, plan preliminar y afectación. |
| Capacidad | Fórmula y límite institucional definidos en RF-12. Si supera el límite, la solicitud se rechaza (política vigente: solo ingreso fijo comprobable). |
| Acciones | Guardar BORRADOR; Simular; Enviar a aprobación; Cancelar BORRADOR con motivo. |
| Bloqueos | No permite otra solicitud abierta del mismo cliente ni envío con expediente/validaciones incompletas. |

> **Terminología visual**
> No usar “PRE-APROBADO”, “APROBACIÓN PROBABLE” u otras expresiones que se confundan con la decisión de Gerencia. Puede mostrarse “Cumple validaciones preliminares” o “No cumple validaciones preliminares”.

# 10.8. P05 - Aprobación de solicitud

- Muestra resumen del cliente, expediente, consentimiento/consulta crediticia, simulación, afectación y condiciones solicitadas.

- Permite a Gerente/Administrador registrar monto aprobado, plazo aprobado, tasa autorizada y primera fecha de vencimiento.

- Antes de confirmar APROBADA, M06 recalcula condiciones y valida capacidad y límite regulatorio aplicable.

- RECHAZADA exige motivo obligatorio.

- PENDIENTE_APROBACION o APROBADA todavía no formalizada puede pasar a CANCELADA únicamente por Gerente/Administrador y con motivo obligatorio.

- Una solicitud ya formalizada deja de gestionarse como solicitud cancelable y sigue el ciclo del préstamo.

# 10.9. P06 - Formalización y documentos

| **Elemento** | **Comportamiento requerido** |
|---|---|
| Condiciones definitivas | Muestra exclusivamente los valores aprobados y validados; no permite editar de forma informal monto, plazo o tasa. |
| Documentos Jasper | Contrato, pagaré y plan de pagos se generan con JasperReports usando datos ya calculados. |
| Checklist | El sistema muestra los documentos obligatorios y su estado. El Oficial registra evidencia; no existe un botón que “fuerce” el cumplimiento. |
| Firma física | Se registra fecha, usuario y copias firmadas que se almacenan dentro de PostgreSQL. |
| Habilitación | Cuando el checklist se completa, el sistema cambia el préstamo a PENDIENTE_DESEMBOLSO. |
| Anulación pre-desembolso | Gerente/Administrador puede cerrar un PENDIENTE_DESEMBOLSO como ANULADO_ANTES_DESEMBOLSO con motivo y cierre documental. |
| Cierre documental posterior | Luego de FINALIZADO, un usuario autorizado registra la cancelación/cierre físico del pagaré u otros documentos sin cambiar el estado financiero FINALIZADO. |

# 10.10. P07 - Caja

- Existe una sola caja física en la V1 y solo puede existir una sesión ABIERTA en todo el sistema.

- Apertura: Cajero ingresa saldo inicial y confirma. El sistema registra responsable y fecha/hora.

- Durante la jornada se muestran saldo teórico y movimientos confirmados que lo explican.

- Todo cobro, desembolso o reversión monetaria requiere caja ABIERTA.

- Cierre: se muestra saldo teórico, el Cajero ingresa saldo físico, el sistema calcula la diferencia y solicita confirmación.

- Una caja CERRADA es histórica e inmutable. No se reabre para corregir operaciones de días anteriores.

- Gerencia puede consultar/controlar caja, pero la operación normal de apertura, cobro, desembolso y cierre pertenece al Cajero.

# 10.11. P08 - Desembolso

| **Bloque** | **Regla de interfaz** |
|---|---|
| Elegibilidad | Solo muestra/permite préstamos PENDIENTE_DESEMBOLSO con documentación completa y sin desembolso válido previo. |
| Caja | Exige sesión ABIERTA y efectivo teórico suficiente antes de habilitar “Confirmar desembolso”. |
| Resumen | Identificador de operación, cliente, monto aprobado, deducciones autorizadas, monto neto a entregar, caja disponible antes y saldo estimado después. |
| Medio | No existe selector de medio de pago. La V1 desembolsa exclusivamente en efectivo. |
| Confirmación | SweetAlert2 o equivalente presenta resumen final. Doble clic/reintento no genera un segundo desembolso. |
| Resultado | Activa el préstamo, descuenta caja, registra auditoría y genera comprobante JasperReports, conservado en PostgreSQL cuando corresponda. |
| Corrección | Un desembolso confirmado no se edita ni elimina; se corrige mediante P10. |

# 10.12. P09 - Cobranza y pagos anticipados

> **Flujo operativo recomendado**
> Buscar cliente/operación -> mostrar deuda actualizada -> ingresar efectivo recibido -> M06 muestra aplicación automática -> confirmar -> calcular/entregar vuelto -> generar recibo. El Cajero no distribuye manualmente el pago entre cuotas o conceptos.

| **Bloque** | **Regla de interfaz** |
|---|---|
| Búsqueda | C.I. o identificador único. Dado que solo existe un préstamo no cerrado por cliente, la identificación debe ser directa. |
| Deuda | Muestra cuotas, vencimientos, saldo, días de atraso cuando corresponda, moratorio, punitorio y monto exigible calculados por M06. |
| Cobro normal/parcial | El Cajero ingresa efectivo recibido. No existe selector de medio. |
| Vuelto | El sistema calcula vuelto. El efecto neto en caja es efectivo recibido menos vuelto. |
| Aplicación | M06 aplica desde la obligación vencida más antigua y, dentro de ella, según RF-25. No hay checkboxes para seleccionar arbitrariamente cuotas. |
| Anticipo parcial | Se procesa como liquidación específica. Reduce capital, mantiene la cuota contractual tanto como sea posible y reduce plazo/número de cuotas remanentes; se genera nueva versión del plan. |
| Cancelación total | M06 calcula el monto exacto a fecha sin intereses futuros no devengados. Saldo cero -\> FINALIZADO financiero. |
| Estados | El sistema determina ACTIVO o EN_MORA según existencia de obligación vencida pendiente. |
| Resultado | Genera recibo JasperReports, saldo posterior y detalle por conceptos; conserva histórico en PostgreSQL. |

> **Separación importante**
> FINALIZADO significa saldo financiero igual a cero. No implica que el pagaré físico ya haya sido marcado como cancelado; el cierre documental se registra posteriormente y de forma separada.

# 10.13. P10 - Reversiones monetarias

- P10 cubre tanto reversión de cobro como reversión de desembolso.

- La reversión parte siempre de una operación confirmada existente y todavía no revertida.

- El Cajero localiza la operación, registra un motivo y solicita autorización.

- Gerente/Administrador revisa y autoriza con su propia identidad. El Cajero nunca introduce ni utiliza credenciales ajenas.

- El original permanece visible e inalterado; la reversión crea su propio registro y comprobante.

- Si la caja original está CERRADA, permanece intacta. El movimiento compensatorio se registra en la caja ABIERTA actual.

- Reversión de cobro: restituye deuda/estado y representa la salida de efectivo que corresponda.

- Reversión de desembolso: restituye estado/saldos y representa el ingreso de efectivo devuelto que corresponda.

- Si no existe caja ABIERTA, no puede confirmarse la reversión monetaria.

# 10.14. P11 - Reportes, cartera y control

| **Salida** | **Contenido mínimo** |
|---|---|
| Indicadores iniciales | Préstamos ACTIVO, préstamos EN_MORA, obligaciones vencidas, próximos vencimientos y cobrado del día. |
| Cartera activa | Operación, cliente, saldo de capital, próxima obligación y estado. |
| Cartera en mora | Cliente, operación, días de atraso, obligaciones vencidas y saldo exigible a la fecha. |
| Vencimientos | Obligaciones a vencer dentro del período seleccionado. |
| Caja | Apertura, cobros, desembolsos, reversiones, saldo teórico, cierre y diferencia. |
| Auditoría | Filtro por usuario, fecha, operación y entidad cuando corresponda. |
| Cumplimiento | Datos estructurados para BCP/Data-OCD, DNIT y SEPRELAD/SIRO según el alcance definido en Documento 01. |

- Los rangos de antigüedad de mora no se inventan en la interfaz. Solo se muestran si existe criterio operativo/regulatorio configurable aprobado.

- Todo reporte imprimible o PDF formal se genera mediante JasperReports.

- Podrán existir exportaciones tabulares cuando el requerimiento lo exija, sin introducir otro motor PDF ni duplicar lógica financiera.

- Los reportes ad hoc regenerables no se almacenan automáticamente como archivo histórico salvo requisito expreso.

# 10.15. P12 - Usuarios y parámetros

- Gerente/Administrador crea, consulta, actualiza, bloquea, reactiva e inactiva usuarios según reglas vigentes.

- Puede restablecer una contraseña; el usuario deberá cambiarla en el siguiente acceso.

- Administra datos institucionales y parámetros autorizados sin alterar snapshots históricos de préstamos/documentos ya emitidos.

- Los límites regulatorios y valores financieros sujetos a vigencia se muestran con período, responsable y trazabilidad.

- La pantalla no permite borrar auditoría ni eliminar físicamente operaciones históricas.

# 10.16. Documentos imprimibles y persistencia histórica

> **Regla obligatoria**
> Todo documento imprimible del SGCPC se genera con JasperReports 6.20.6. No se utilizará un segundo motor PDF. Las plantillas .jrxml/.jasper forman parte del WAR; los PDFs históricos que deban conservarse se almacenan dentro de PostgreSQL.

| **Documento** | **Módulo** | **Contenido mínimo / regla** |
|---|---|---|
| Contrato de crédito | M05 | Operación, partes, condiciones aprobadas, TNA/TEA/CFT, datos institucionales, versión de plantilla y firmas. |
| Pagaré | M05 | Número, operación, partes, monto/conceptos y texto jurídico aprobado; versión generada y copia firmada pueden conservarse por separado. |
| Plan de pagos / cuotera | M05/M06 | Número, vencimiento, capital, interés, tributos, total y saldo. |
| Comprobante de desembolso | M07 | Operación, cliente, monto aprobado, deducciones, monto neto en efectivo, fecha/hora, Cajero y efecto de caja. |
| Recibo de cobro | M07 | Número, operación, cliente, efectivo recibido, vuelto, monto aplicado, detalle por conceptos, saldo posterior, fecha/hora y Cajero. |
| Comprobante de reversión | M07 | Tipo/original, motivo, solicitante/procesador, autorizador, fecha/hora y efecto compensatorio en deuda/caja. |
| Liquidación anticipada | M06/M07 | Tipo parcial/total, cálculo a fecha, importe exigible, efecto en capital y referencia al plan actualizado cuando corresponda. |
| Cierre de caja | M07 | Sesión, Cajero, saldo inicial, movimientos, saldo teórico, saldo físico, diferencia y cierre. |

# 10.17. Visualización y manejo de archivos

- La interfaz permite cargar, ver y descargar documentos autorizados, pero el navegador nunca recibe una ruta física del servidor como fuente oficial.

- ControladorDocumentosServlet y ServicioDocumentos validan sesión, perfil, contexto, tipo y tamaño antes de entregar o persistir contenido.

- Los metadatos visibles pueden incluir tipo, nombre, fecha, estado, versión y usuario de carga; el contenido binario vive en PostgreSQL.

- Los documentos generados y los posteriormente firmados/escaneados pueden coexistir como versiones distintas para conservar historia.

- Los formatos de carga de expediente de la V1 son PDF, JPG/JPEG y PNG; el tamaño máximo será parametrizable conforme al Documento 08.

# 10.18. Reglas de formato, estados y mensajes

| **Elemento** | **Regla** |
|---|---|
| Dinero | Mostrar G. o Gs. de forma uniforme; separador de miles; sin decimales en importes PYG cuando no correspondan. |
| Porcentajes | Mostrar precisión legible en pantalla; la precisión interna es la definida por M06/Documento 08. |
| Fechas | dd/mm/aaaa en pantalla; fecha/hora completa en comprobantes y auditoría. |
| Estados | Usar exactamente los estados oficiales; no crear sinónimos de pantalla. |
| Solicitud | BORRADOR, PENDIENTE_APROBACION, APROBADA, RECHAZADA, CANCELADA. |
| Préstamo | PENDIENTE_DESEMBOLSO, ACTIVO, EN_MORA, FINALIZADO, ANULADO_ANTES_DESEMBOLSO. |
| Cuota | El estado de pago se representa según Documento 08; el vencimiento se deriva de fecha + saldo, evitando mezclar conceptos incompatibles. |
| Errores | Explicar qué ocurrió y qué debe hacer el usuario; nunca mostrar SQL, stacktrace, ruta de servidor ni secretos. |
| Confirmaciones | Usar acciones explícitas: “Confirmar desembolso”, “Confirmar cobro”, “Cerrar caja”, “Solicitar reversión”, “Autorizar reversión”. |

# 10.19. Correspondencia de pantallas con MVC

| **Pantalla** | **Controlador Servlet** | **Servicio(s) principal(es)** |
|---|---|---|
| P01 | ControladorAutenticacionServlet / ControladorUsuariosServlet | ServicioAutenticacion / ServicioUsuarios |
| P02 | Controladores según acceso rápido | Servicios correspondientes al perfil |
| P03 | ControladorClientesServlet / ControladorDocumentosServlet | ServicioClientes / ServicioDocumentos / ServicioConsultaCrediticia |
| P04 | ControladorSolicitudesServlet | ServicioSolicitudes / ServicioEvaluacionCredito / ServicioMotorFinanciero |
| P05 | ControladorAprobacionesServlet | ServicioAprobacionCredito / ServicioMotorFinanciero |
| P06 | ControladorFormalizacionServlet / ControladorDocumentosServlet | ServicioFormalizacion / ServicioDocumentos / ServicioMotorFinanciero |
| P07 | ControladorCajaServlet | ServicioCaja |
| P08 | ControladorDesembolsosServlet | ServicioDesembolso / ServicioCaja |
| P09 | ControladorCobranzasServlet | ServicioCobranza / ServicioMotorFinanciero / ServicioCaja |
| P10 | ControladorReversionesServlet | ServicioReversiones / ServicioCaja / ServicioAuditoria |
| P11 | ControladorReportesServlet | ServicioReportes |
| P12 | ControladorUsuariosServlet / ControladorParametrosServlet | ServicioUsuarios / ServicioParametros |

> **Regla MVC**
> La tabla indica responsabilidad principal. Una mejora UI/UX no autoriza a saltarse la arquitectura: JSP -> Servlet -> Servicio -> DAO -> PostgreSQL. Las plantillas Jasper reciben datos ya calculados; no implementan reglas financieras.

# 10.20. Criterios de aceptación de interfaz y reportes

| **Criterio** | **Condición de aprobación** |
|---|---|
| Claridad | La acción principal y el estado de la operación son comprensibles sin conocimiento técnico. |
| Perfil | Las acciones visibles y permitidas coinciden con el rol, y la autorización también se valida en servidor. |
| Consistencia | El mismo concepto conserva nombre, estado y significado en todos los módulos. |
| Cálculo | Los valores financieros mostrados provienen de ServicioMotorFinanciero y no se recalculan en JSP/JavaScript/Jasper. |
| Caja | Todas las operaciones monetarias son efectivo, requieren caja ABIERTA y reflejan correctamente saldo, vuelto y movimientos compensatorios. |
| Documentos | Todo imprimible usa JasperReports; todo archivo histórico conservable reside en PostgreSQL. |
| Trazabilidad | Solicitud, préstamo, recibo/comprobante, usuario y fecha/hora pueden identificarse en operaciones críticas. |
| Idempotencia | Doble clic, refresh o reintento no crea dos desembolsos, pagos o reversiones válidas. |
| UI/UX | Se permiten mejoras estéticas y de distribución sin alterar reglas, permisos, datos obligatorios ni secuencias funcionales. |
| Reportes | Los reportes usan datos reales y filtros explícitos; si no hay información, no inventan resultados. |
| Proporcionalidad | La interfaz sigue siendo simple para un solo local, pocos usuarios y una única caja física. |

> **Estado del Documento 10**
> DISEÑO DE INTERFACES Y ESPECIFICACIÓN DE REPORTES FINAL PARA DESARROLLO. Este documento reemplaza los bocetos anteriores como referencia funcional. Cloud Code puede mejorar la presentación visual dentro de estas reglas, pero no modificar comportamiento, arquitectura, permisos, estados, reglas financieras ni tecnología de documentos por cuenta propia.

Capital Crédito Paraguay S.R.L. - SGCPC \| Documento 10 FINAL
