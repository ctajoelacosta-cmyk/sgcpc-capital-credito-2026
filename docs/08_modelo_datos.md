> **Documento de referencia (versión FINAL original).** Donde difiera de `00_especificacion_maestra.md`, prevalece el Documento 00. En particular: el modelo de datos v1 tiene 27 tablas (ver Doc. 00 §10), la conexión es por `bd.properties` y no por JNDI (Doc. 00 §14.4), y se agregan las pantallas P13 y P14 (Doc. 00 §12).

**SISTEMA DE GESTIÓN DE CRÉDITOS  
PERSONALES Y COBRANZAS**

**DOCUMENTO 08**

**MODELO RELACIONAL Y DICCIONARIO DE DATOS**

Capital Crédito Paraguay S.R.L. - Versión FINAL normalizada para PostgreSQL y desarrollo

| **Motor de base de datos** | PostgreSQL mediante JDBC 42.7.1. |
|---|---|
| **Alcance** | Único local, una caja física, pocos usuarios y préstamos personales en PYG. |
| **Documentación** | Todos los archivos de negocio se almacenan dentro de PostgreSQL; no existen rutas locales como repositorio documental. |
| **Base funcional** | Documentos 01 a 07 en versión FINAL y aprobada. |
| **Finalidad** | Definir el modelo normalizado que deberá implementar el desarrollo sin inventar tablas, estados, relaciones ni reglas de persistencia. |

| **Objetivo.** Definir una base de datos PostgreSQL simple, normalizada, trazable y portable para la versión inicial del SGCPC. El modelo deberá permitir que el sistema sea trasladado al servidor del cliente mediante el archivo WAR y una restauración completa de la base de datos, incluyendo documentos y archivos históricos. |
|---|

# 8.1. Alcance técnico y decisiones ya aprobadas

- PostgreSQL es el gestor de base de datos oficial de la versión inicial. La aplicación Java 8 accederá mediante JDBC 42.7.1 desde la capa DAO/Repositorio.

- La versión inicial opera en un único local, con una sola caja física y tres perfiles internos: Oficial de Crédito, Gerente/Administrador y Cajero.

- La moneda operativa es PYG y todos los movimientos monetarios se realizan exclusivamente en efectivo. No se modelan transferencias, cheques ni otros medios de pago o desembolso.

- Todo desembolso, cobro y movimiento compensatorio monetario requiere una sesión de caja ABIERTA. Las sesiones CERRADAS son históricas e inmutables.

- Un cliente puede tener como máximo una solicitud abierta a la vez (BORRADOR, PENDIENTE_APROBACION o APROBADA) y un préstamo no cerrado que bloquee nueva originación (PENDIENTE_DESEMBOLSO, ACTIVO o EN_MORA).

- Los archivos de negocio, adjuntos y documentos históricos se almacenan dentro de PostgreSQL. No se utilizarán carpetas del servidor o de las estaciones de trabajo como repositorio documental.

- JasperReports genera todos los documentos imprimibles. Cuando un documento deba conservarse históricamente, el PDF producido por JasperReports se persiste en PostgreSQL junto con sus metadatos.

- Los cálculos financieros pertenecen exclusivamente al M06. La base persiste datos y resultados confirmados; no replica fórmulas financieras en disparadores, vistas SQL o procedimientos aislados.

- Las operaciones financieras confirmadas no se eliminan para corregir errores. Cobros y desembolsos se corrigen mediante registros de reversión autorizados y trazables.

| **Regla para el desarrollo.** Este documento define el modelo oficial. Cloud Code podrá decidir detalles de implementación JDBC o SQL auxiliares, pero no podrá cambiar nombres conceptuales, estados, cardinalidades, reglas de integridad ni crear estructuras paralelas sin actualizar primero la documentación aprobada. |
|---|

# 8.2. Principios de diseño, normalización y tipos

- El modelo se diseña en Tercera Forma Normal (3FN) como criterio general. Se separan cabeceras y detalles, planes y cuotas, pagos y aplicaciones, archivos binarios y metadatos documentales.

- Las repeticiones históricas deliberadas -por ejemplo ingreso evaluado, tasas aprobadas o monto desembolsado- se consideran fotografías de una operación y no duplicación accidental. Se conservan para poder reconstruir lo que fue aprobado y firmado en su momento.

- Las personas se registran una sola vez en personas.

- Los archivos físicos se centralizan en archivos_binarios. Las entidades del negocio guardan referencias al archivo; ninguna tabla almacenará rutas locales como fuente oficial del documento.

- Los planes de pago se separan en planes_pago y cuotas. Un anticipo parcial genera una nueva versión del plan sin sobrescribir la versión histórica anterior.

- Los estados y perfiles fijos se controlan mediante CHECK o enumeración equivalente en Java. Los catálogos que necesitan administración, como tipos documentales, se modelan en tablas propias.

- Las bajas funcionales se resuelven mediante estados INACTIVA, CANCELADA, ANULADO_ANTES_DESEMBOLSO, REVERTIDO u otros definidos; no mediante DELETE de información histórica.

| **Concepto** | **Convención PostgreSQL** | **Criterio** |
|---|---|---|
| Identificadores | BIGINT | PK generada por secuencia/identity. El script SQL definitivo usará una sintaxis compatible con la versión PostgreSQL del servidor. |
| Dinero PYG | NUMERIC(18,0) | Guaraníes enteros. No usar REAL, FLOAT o DOUBLE PRECISION para importes. |
| Tasas / porcentajes | NUMERIC(11,6) | Se almacenan como porcentaje: 24.000000 significa 24 %, no 0,24. M06 realiza conversiones. |
| Fechas de negocio | DATE | Vencimientos, fechas de documento y períodos cuando no se requiere hora. |
| Eventos / auditoría | TIMESTAMP | Fecha y hora del servidor; el entorno de producción deberá operar con zona horaria Paraguay. |
| Archivos | BYTEA | Contenido binario dentro de PostgreSQL. |
| Auditoría estructurada | JSONB | Valor anterior/nuevo cuando se requiera conservar estructura sin depender de texto libre. |
| Booleanos | BOOLEAN | Indicadores simples como vigente, requiere_firma o debe_cambiar_contrasena. |

# 8.3. Estructura general del modelo

El modelo final se compone de 22 tablas. La cantidad de tablas responde a normalización, trazabilidad y almacenamiento documental; no significa que el usuario final tendrá 22 pantallas o módulos.

| **N.º** | **Tabla** | **Módulo** | **Responsabilidad principal** |
|---|---|---|---|
| 01 | usuarios | M01 | Usuarios internos, perfiles, bloqueo e información de credenciales. |
| 02 | parametros_sistema | M01 | Datos institucionales y parámetros configurables con vigencia. |
| 03 | limites_tasa_usura | M01/M06 | Histórico de límites regulatorios utilizados por M06. |
| 04 | personas | M02 | Ficha única de cliente. |
| 05 | archivos_binarios | Transversal | Contenido BYTEA y metadatos de todos los archivos conservados en PostgreSQL. |
| 06 | tipos_documento | M02/M05 | Catálogo documental y reglas del checklist. |
| 07 | documentos_expediente | M02/M05 | Vinculación semántica de archivos con persona, solicitud o préstamo. |
| 08 | consentimientos_crediticios | M02 | Consentimiento previo a consulta crediticia. |
| 09 | consultas_crediticias | M02/M03 | Resultado y trazabilidad del buró/proveedor. |
| 10 | solicitudes_credito | M03/M04 | Solicitud, evaluación, decisión y cancelación. |
| 11 | prestamos | M05/M06/M07 | Operación formalizada y estados del préstamo. |
| 12 | cargos_operacion | M05/M06 | Comisiones, gastos, tributos u otros conceptos informados antes de la firma. |
| 13 | planes_pago | M06 | Cabecera y versionado de planes de amortización. |
| 14 | cuotas | M06 | Detalle de vencimientos y componentes de cada plan. |
| 15 | liquidaciones_anticipadas | M06 | Cálculo histórico de anticipo parcial o cancelación total. |
| 16 | sesiones_caja | M07 | Apertura, cierre y arqueo de la caja física. |
| 17 | desembolsos | M07 | Entregas de efectivo confirmadas. |
| 18 | reversiones_desembolso | M07 | Corrección autorizada de desembolso sin borrar el original. |
| 19 | pagos | M07 | Cobros confirmados y recibos. |
| 20 | aplicaciones_pago | M06/M07 | Detalle de distribución de cada pago. |
| 21 | reversiones_pago | M07 | Corrección autorizada de cobro sin borrar el original. |
| 22 | auditoria_operaciones | Transversal | Bitácora inalterable de acciones críticas. |

# 8.4. Diagrama entidad-relación simplificado

La figura muestra las relaciones principales del ciclo de crédito, caja, documentos y cobranza. El diccionario de datos de la sección 8.6 contiene el detalle completo de campos y restricciones.

<img src="media/image1.png" style="width:7.15in;height:4.26274in" />

*Figura 1. Modelo relacional simplificado del SGCPC.*

# 8.5. Reglas de integridad relacional y de estados

- personas.cedula_identidad será única.

- Una solicitud tendrá un cliente obligatorio.

- Existirá como máximo una solicitud abierta por cliente. PostgreSQL deberá reforzarlo mediante índice único parcial para BORRADOR, PENDIENTE_APROBACION y APROBADA.

- Una solicitud formalizada originará como máximo un préstamo; prestamos.id_solicitud será UNIQUE.

- El estado del préstamo podrá ser PENDIENTE_DESEMBOLSO, ACTIVO, EN_MORA, FINALIZADO o ANULADO_ANTES_DESEMBOLSO.

- ACTIVO y EN_MORA son estados derivados de la situación de la deuda luego del desembolso: si existe obligación vencida con saldo, el préstamo se refleja EN_MORA; al regularizar lo vencido y quedar saldo futuro, vuelve a ACTIVO.

- FINALIZADO significa saldo financiero total igual a cero. El cierre físico/documental se registra separadamente mediante estado_cierre_documental.

- La cuota no utilizará VENCIDA como estado excluyente. estado_pago será PENDIENTE, PARCIAL o PAGADA; la condición de vencimiento se determina por fecha_vencimiento y saldo pendiente.

- Una sesión de caja CERRADA no puede reabrirse ni modificarse para corregir movimientos. Toda reversión posterior utiliza una sesión ABIERTA actual.

- Solo podrá existir una sesión de caja ABIERTA en todo el sistema, por tratarse de una única caja física en la V1.

- Todo pago, desembolso y reversión monetaria debe referenciar una sesión de caja ABIERTA al momento de confirmarse.

- Los documentos y archivos binarios no se eliminan desde funciones normales. Las versiones reemplazadas o documentos cerrados conservan su contenido histórico.

- Los registros de auditoría son de solo inserción desde la aplicación y no podrán modificarse mediante funciones operativas.

# 8.6. Diccionario de datos

Las definiciones siguientes son la fuente oficial para el esquema PostgreSQL. Los nombres están en español y minúsculas con guion bajo. Las PK/FK se mantendrán con estos conceptos aunque el script SQL final ajuste sintaxis de generación de identificadores.

## Tabla 01. usuarios

Usuarios internos de la aplicación. No se eliminan usuarios con historial; se inactivan.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_usuario | BIGINT | PK | Identificador interno. |
| nombre_usuario | VARCHAR(60) | UNIQUE, NOT NULL | Nombre de acceso. |
| contrasena_hash | VARCHAR(255) | NOT NULL | Hash BCrypt; nunca texto plano. |
| nombres | VARCHAR(100) | NOT NULL | Nombres del empleado. |
| apellidos | VARCHAR(100) | NOT NULL | Apellidos del empleado. |
| cedula_identidad | VARCHAR(20) | UNIQUE, NOT NULL | C.I. del usuario interno. |
| perfil_usuario | VARCHAR(30) | CHECK, NOT NULL | OFICIAL_CREDITO, GERENTE_ADMINISTRADOR o CAJERO. |
| estado_cuenta | VARCHAR(20) | CHECK, NOT NULL | ACTIVA, BLOQUEADA o INACTIVA. |
| intentos_fallidos | SMALLINT | NOT NULL DEFAULT 0 | Contador de intentos fallidos consecutivos. |
| debe_cambiar_contrasena | BOOLEAN | NOT NULL DEFAULT FALSE | TRUE tras restablecimiento administrativo. |
| fecha_ultimo_acceso | TIMESTAMP | NULL | Último acceso correcto. |
| fecha_cambio_contrasena | TIMESTAMP | NULL | Último cambio de contraseña. |
| fecha_creacion | TIMESTAMP | NOT NULL | Fecha/hora de alta. |
| creado_por | BIGINT | FK usuarios, NULL | Usuario creador; NULL solo para bootstrap inicial. |
| fecha_inactivacion | TIMESTAMP | NULL | Momento de inactivación cuando aplique. |

## Tabla 02. parametros_sistema

Parámetros institucionales y operativos administrables sin recompilar. Cuando un valor requiera histórico se crea una nueva vigencia en lugar de sobrescribir la anterior.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_parametro | BIGINT | PK | Identificador. |
| clave_parametro | VARCHAR(80) | NOT NULL | Clave estable, ej. razon_social o porcentaje_maximo_afectacion. |
| nombre_parametro | VARCHAR(150) | NOT NULL | Nombre visible. |
| valor_parametro | VARCHAR(500) | NOT NULL | Valor serializado según tipo_valor. |
| tipo_valor | VARCHAR(20) | CHECK, NOT NULL | TEXTO, ENTERO, DECIMAL, BOOLEANO o FECHA. |
| vigente_desde | DATE | NOT NULL | Inicio de vigencia. |
| vigente_hasta | DATE | NULL | Fin de vigencia; NULL si actual. |
| modificado_por | BIGINT | FK usuarios, NOT NULL | Responsable del registro. |
| fecha_modificacion | TIMESTAMP | NOT NULL | Fecha/hora del alta o cambio. |
| observacion | VARCHAR(300) | NULL | Aclaración opcional. |

## Tabla 03. limites_tasa_usura

Histórico de límites máximos efectivos publicados y utilizados por M06.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_limite_tasa | BIGINT | PK | Identificador. |
| vigente_desde | DATE | NOT NULL | Inicio del período aplicable. |
| vigente_hasta | DATE | NULL | Fin del período. |
| tasa_maxima_tea | NUMERIC(11,6) | NOT NULL | Porcentaje máximo efectivo anual. |
| fuente_referencia | VARCHAR(300) | NOT NULL | Referencia oficial utilizada. |
| registrado_por | BIGINT | FK usuarios, NOT NULL | Usuario que cargó el límite. |
| fecha_registro | TIMESTAMP | NOT NULL | Fecha/hora de carga. |

## Tabla 04. personas

Ficha única de personas naturales.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_persona | BIGINT | PK | Identificador. |
| cedula_identidad | VARCHAR(20) | UNIQUE, NOT NULL | C.I. normalizada. |
| ruc | VARCHAR(20) | NULL | RUC cuando corresponda. |
| nombres | VARCHAR(100) | NOT NULL | Nombres según documento. |
| apellidos | VARCHAR(100) | NOT NULL | Apellidos según documento. |
| fecha_nacimiento | DATE | NOT NULL | Fecha de nacimiento. |
| estado_civil | VARCHAR(30) | NOT NULL | Estado civil informado. |
| domicilio | VARCHAR(250) | NOT NULL | Domicilio actual. |
| direccion_laboral | VARCHAR(250) | NULL | Dirección laboral. |
| telefono_whatsapp | VARCHAR(30) | NOT NULL | Contacto principal. |
| correo_electronico | VARCHAR(150) | NULL | Correo si dispone. |
| ingreso_neto_comprobado | NUMERIC(18,0) | NOT NULL | Ingreso actual comprobado en PYG. |
| referencias | TEXT | NULL | Referencias actuales. |
| estado_persona | VARCHAR(20) | CHECK, NOT NULL | ACTIVA o INACTIVA. |
| registrado_por | BIGINT | FK usuarios, NOT NULL | Usuario de alta. |
| fecha_registro | TIMESTAMP | NOT NULL | Fecha/hora de alta. |
| fecha_actualizacion | TIMESTAMP | NOT NULL | Última actualización de ficha actual. |

## Tabla 05. archivos_binarios

Repositorio binario interno de PostgreSQL. Ningún archivo de negocio depende de rutas del sistema operativo.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_archivo | BIGINT | PK | Identificador del archivo. |
| nombre_archivo | VARCHAR(255) | NOT NULL | Nombre original o descriptivo. |
| mime_type | VARCHAR(100) | NOT NULL | Ej. application/pdf o image/jpeg. |
| extension | VARCHAR(10) | NOT NULL | pdf, jpg, jpeg o png en la V1. |
| tamano_bytes | BIGINT | NOT NULL | Tamaño real; validado contra parámetro máximo. |
| contenido | BYTEA | NOT NULL | Contenido binario completo. |
| hash_sha256 | CHAR(64) | NOT NULL | Huella SHA-256 para control de integridad. |
| origen_archivo | VARCHAR(20) | CHECK, NOT NULL | CARGADO o JASPER. |
| fecha_carga | TIMESTAMP | NOT NULL | Fecha/hora de incorporación. |
| cargado_por | BIGINT | FK usuarios, NOT NULL | Usuario que cargó o generó el archivo. |

## Tabla 06. tipos_documento

Catálogo de documentos y reglas del checklist. Permite exigir documentos sin hardcodear cada tipo en JSP o DAO.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_tipo_documento | BIGINT | PK | Identificador. |
| codigo_tipo | VARCHAR(50) | UNIQUE, NOT NULL | Código estable: CEDULA, CONTRATO_FIRMADO, etc. |
| nombre_tipo | VARCHAR(120) | NOT NULL | Descripción visible. |
| contexto | VARCHAR(20) | CHECK, NOT NULL | PERSONA, SOLICITUD o PRESTAMO. |
| obligatorio_desembolso | BOOLEAN | NOT NULL | Indica si integra checklist previo al desembolso. |
| aplica_cliente | BOOLEAN | NOT NULL | Aplica al cliente. Nunca se llegó a consultar en ningún servicio; eliminada en la ronda de columnas sin uso (18/10/2026). |
| aplica_codeudor | BOOLEAN | NOT NULL | Eliminada junto con el resto de codeudor/garante (Commit 3 del retiro, 18/10/2026) — ya no existe en el esquema. |
| requiere_firma | BOOLEAN | NOT NULL | Indica si debe existir evidencia firmada. Nunca se llegó a consultar (el checklist usa obligatorio_desembolso); eliminada en la ronda de columnas sin uso (18/10/2026). |
| origen_esperado | VARCHAR(20) | CHECK, NOT NULL | CARGADO, JASPER o CUALQUIERA. Nunca se llegó a consultar; eliminada en la ronda de columnas sin uso (18/10/2026). |
| activo | BOOLEAN | NOT NULL | Permite retirar un tipo sin borrar históricos. |

## Tabla 07. documentos_expediente

Vincula un archivo binario con su significado y contexto de negocio. Una misma operación puede conservar documento generado y copia firmada como registros distintos.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_documento | BIGINT | PK | Identificador documental. |
| id_archivo | BIGINT | FK archivos_binarios, NOT NULL | Contenido físico conservado en PostgreSQL. |
| id_tipo_documento | BIGINT | FK tipos_documento, NOT NULL | Tipo semántico del documento. |
| id_persona | BIGINT | FK personas, NULL | Persona relacionada, si aplica. |
| id_solicitud | BIGINT | FK solicitudes_credito, NULL | Solicitud relacionada, si aplica. |
| id_prestamo | BIGINT | FK prestamos, NULL | Préstamo relacionado, si aplica. |
| version_documento | SMALLINT | NOT NULL DEFAULT 1 | Versión lógica del documento. |
| estado_documento | VARCHAR(20) | CHECK, NOT NULL | VIGENTE, REEMPLAZADO, CERRADO o ANULADO. |
| id_documento_reemplazado | BIGINT | FK documentos_expediente, NULL | Documento anterior reemplazado, si corresponde. |
| fecha_documento | DATE | NULL | Fecha propia del documento. |
| fecha_cierre | TIMESTAMP | NULL | Cierre/cancelación documental. |
| cerrado_por | BIGINT | FK usuarios, NULL | Usuario que registró cierre. |
| observacion | VARCHAR(500) | NULL | Nota controlada. |

## Tabla 08. consentimientos_crediticios

Evidencia fehaciente del consentimiento previo a cada consulta crediticia.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_consentimiento | BIGINT | PK | Identificador. |
| id_persona | BIGINT | FK personas, NOT NULL | Titular del consentimiento. |
| id_solicitud | BIGINT | FK solicitudes_credito, NULL | Solicitud relacionada. |
| id_documento_evidencia | BIGINT | FK documentos_expediente, NOT NULL | Evidencia almacenada. |
| finalidad | VARCHAR(250) | NOT NULL | Finalidad informada. |
| fecha_consentimiento | TIMESTAMP | NOT NULL | Momento del consentimiento. |
| registrado_por | BIGINT | FK usuarios, NOT NULL | Usuario que registró. |
| fecha_registro | TIMESTAMP | NOT NULL | Momento de incorporación. |

## Tabla 09. consultas_crediticias

Resultado de la consulta efectivamente recibida. No genera scoring propio.

*(D-39, AVANCE.md)* `id_solicitud` es NULL, no NOT NULL como decía la versión original de este documento: RN-24/PO-02 registran el resultado de la consulta al buró durante el registro del cliente (P03/E3), antes de que exista ninguna solicitud (eso es P04/E4). Cómo se vincula esta consulta con la solicitud que se cree después queda pendiente para el plan de E4.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_consulta_crediticia | BIGINT | PK | Identificador. |
| id_persona | BIGINT | FK personas, NOT NULL | Persona consultada. |
| id_solicitud | BIGINT | FK solicitudes_credito, NULL | Solicitud evaluada, si ya existe (D-39). |
| id_consentimiento | BIGINT | FK consentimientos_crediticios, NOT NULL | Consentimiento respaldo. |
| proveedor_buro | VARCHAR(80) | NOT NULL | Proveedor consultado. |
| fecha_hora_consulta | TIMESTAMP | NOT NULL | Momento de consulta. |
| consultado_por | BIGINT | FK usuarios, NOT NULL | Oficial responsable. |
| puntaje_obtenido | NUMERIC(10,2) | NULL | Solo si proveedor lo entrega. |
| clasificacion_obtenida | VARCHAR(100) | NULL | Clasificación literal del proveedor. |
| indicador_mora | BOOLEAN | NULL | Dato recibido cuando exista. |
| resumen_resultado | TEXT | NULL | Resumen sin reinterpretar. |
| id_documento_resultado | BIGINT | FK documentos_expediente, NULL | Archivo de respaldo del resultado. |

## Tabla 10. solicitudes_credito

Solicitud, fotografía de evaluación y decisión gerencial. Conserva por separado lo solicitado, simulado y aprobado.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_solicitud | BIGINT | PK | Identificador interno. |
| codigo_solicitud | VARCHAR(40) | UNIQUE, NOT NULL | Código visible. |
| id_cliente | BIGINT | FK personas, NOT NULL | Solicitante. |
| id_codeudor | BIGINT | FK personas, NULL | Sin uso desde el retiro de codeudor (18/10/2026) — ningún servicio la escribe ni la lee; pendiente de eliminar junto con la columna en el Commit 3 del refactor. |
| creado_por | BIGINT | FK usuarios, NOT NULL | Oficial creador. |
| fecha_solicitud | TIMESTAMP | NOT NULL | Alta. |
| monto_solicitado | NUMERIC(18,0) | NOT NULL | Monto pedido. |
| plazo_solicitado_meses | SMALLINT | NOT NULL | Plazo pedido. |
| destino_credito | VARCHAR(250) | NOT NULL | Destino declarado. |
| primera_fecha_vencimiento_solicitada | DATE | NOT NULL | Primera fecha solicitada. |
| tasa_tna_propuesta | NUMERIC(11,6) | NOT NULL | TNA de simulación. |
| tasa_tea_simulada | NUMERIC(11,6) | NOT NULL | TEA simulada por M06. |
| cft_simulado | NUMERIC(11,6) | NULL | CFT simulado. |
| cuota_simulada | NUMERIC(18,0) | NOT NULL | Cuota de evaluación. |
| ingreso_cliente_evaluado | NUMERIC(18,0) | NOT NULL | Snapshot de ingreso cliente. |
| ingreso_codeudor_evaluado | NUMERIC(18,0) | NULL | Sin uso desde el retiro de codeudor (18/10/2026) — ningún servicio la escribe ni la lee; pendiente de eliminar junto con la columna en el Commit 3 del refactor. |
| limite_afectacion_aplicado | NUMERIC(7,4) | NOT NULL | Política usada, inicialmente 40 %. |
| porcentaje_afectacion | NUMERIC(7,4) | NOT NULL | Resultado de capacidad. |
| estado_solicitud | VARCHAR(30) | CHECK, NOT NULL | BORRADOR, PENDIENTE_APROBACION, APROBADA, RECHAZADA o CANCELADA. |
| fecha_envio_aprobacion | TIMESTAMP | NULL | Momento de envío. |
| monto_aprobado | NUMERIC(18,0) | NULL | Monto autorizado. |
| plazo_aprobado_meses | SMALLINT | NULL | Plazo autorizado. |
| tasa_tna_aprobada | NUMERIC(11,6) | NULL | TNA definitiva. |
| tasa_tea_aprobada | NUMERIC(11,6) | NULL | TEA definitiva. |
| cft_aprobado | NUMERIC(11,6) | NULL | CFT definitivo. |
| primera_fecha_vencimiento_aprobada | DATE | NULL | Primera fecha definitiva. |
| id_limite_tasa_aplicado | BIGINT | FK limites_tasa_usura, NULL | Límite usado al aprobar. |
| decidido_por | BIGINT | FK usuarios, NULL | Gerente que decide. |
| fecha_decision | TIMESTAMP | NULL | Aprobación/rechazo. |
| motivo_rechazo | TEXT | NULL | Obligatorio si RECHAZADA. |
| cancelado_por | BIGINT | FK usuarios, NULL | Usuario autorizado que cancela. |
| fecha_cancelacion | TIMESTAMP | NULL | Momento de cancelación. |
| motivo_cancelacion | TEXT | NULL | Obligatorio si CANCELADA. |
| formalizada | BOOLEAN | NOT NULL DEFAULT FALSE | E5/D-68: TRUE una vez que existe un préstamo para esta solicitud. Una APROBADA con `formalizada = TRUE` ya no cuenta como "abierta" para RN-01/02/28 (la solicitud nunca cambia de estado_solicitud al formalizarse). |

## Tabla 11. prestamos

Operación formalizada. Conserva condiciones contractuales definitivas y separa finalización financiera de cierre documental.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_prestamo | BIGINT | PK | Identificador interno. |
| identificador_operacion | VARCHAR(50) | UNIQUE, NOT NULL | Identificador oficial de operación. |
| id_solicitud | BIGINT | UNIQUE, FK solicitudes_credito | Una solicitud origina máximo un préstamo. |
| numero_contrato | VARCHAR(50) | UNIQUE, NULL | Número contractual si se utiliza. |
| numero_pagare | VARCHAR(50) | UNIQUE, NOT NULL | Número de pagaré. |
| monto_aprobado | NUMERIC(18,0) | NOT NULL | Capital contractual. |
| plazo_meses | SMALLINT | NOT NULL | Plazo contractual inicial. |
| tasa_tna | NUMERIC(11,6) | NOT NULL | TNA contractual. |
| tasa_tea | NUMERIC(11,6) | NOT NULL | TEA contractual. |
| cft | NUMERIC(11,6) | NULL | CFT contractual. |
| tasa_moratoria | NUMERIC(11,6) | NOT NULL | Tasa moratoria pactada. |
| tasa_punitoria | NUMERIC(11,6) | NOT NULL | *(D-82, AVANCE.md)* Fracción del moratorio que se cobra como punitorio (ej. 0,30 = 30 % del moratorio ya calculado, RN-16/§6.4) -- **NO es una tasa anual**, no se multiplica por `tasa_moratoria`. Se guarda tal cual viene de `credito.porcentaje_punitorio` al formalizar. |
| primera_fecha_vencimiento | DATE | NOT NULL | Primera fecha contractual. |
| fecha_formalizacion | TIMESTAMP | NOT NULL | Momento de formalización. |
| fecha_desembolso | DATE | NULL | *(D-23, AVANCE.md)* Fecha del desembolso CONFIRMADO; espejo de `desembolsos.fecha_hora_desembolso`, guardada acá para que `ServicioMotorFinanciero` no tenga que hacer join al prorratear el interés de la cuota 1 (§6.6/6.7). Null mientras el préstamo está PENDIENTE_DESEMBOLSO. |
| estado_prestamo | VARCHAR(35) | CHECK, NOT NULL | PENDIENTE_DESEMBOLSO, ACTIVO, EN_MORA, FINALIZADO o ANULADO_ANTES_DESEMBOLSO. |
| fecha_finalizacion_financiera | TIMESTAMP | NULL | Momento en que saldo llega a cero. |
| motivo_anulacion_sin_desembolso | TEXT | NULL | Obligatorio si ANULADO_ANTES_DESEMBOLSO. |
| fecha_anulacion_sin_desembolso | TIMESTAMP | NULL | Momento de anulación. |
| anulado_por | BIGINT | FK usuarios, NULL | Gerente/Administrador que autoriza. |
| estado_cierre_documental | VARCHAR(20) | CHECK, NOT NULL | PENDIENTE, COMPLETO o NO_APLICA. |
| fecha_cierre_documental | TIMESTAMP | NULL | Momento del cierre físico/documental. |
| cierre_documental_por | BIGINT | FK usuarios, NULL | Usuario que confirma cierre documental. |
| formalizado_por | BIGINT | FK usuarios, NOT NULL | Usuario que completa formalización. |

## Tabla 12. cargos_operacion

Desglose normalizado de comisiones, gastos, tributos u otros conceptos conocidos y autorizados antes de la firma.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_cargo_operacion | BIGINT | PK | Identificador. |
| id_prestamo | BIGINT | FK prestamos, NOT NULL | Préstamo relacionado. |
| codigo_concepto | VARCHAR(50) | NOT NULL | Código estable del concepto. |
| descripcion_concepto | VARCHAR(180) | NOT NULL | Descripción visible. |
| tipo_concepto | VARCHAR(20) | CHECK, NOT NULL | COMISION, GASTO, TRIBUTO u OTRO. |
| monto | NUMERIC(18,0) | NOT NULL | Importe en PYG. |
| descuenta_desembolso | BOOLEAN | NOT NULL | Si reduce el efectivo neto entregado. |
| incluye_en_cft | BOOLEAN | NOT NULL | Si forma parte del CFT según regla vigente. |
| orden_presentacion | SMALLINT | NOT NULL | Orden en contrato/comprobante. |

## Tabla 13. planes_pago

Cabecera de cada versión del plan de amortización. Evita repetir versión y vigencia en cada cuota.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_plan_pago | BIGINT | PK | Identificador. |
| id_prestamo | BIGINT | FK prestamos, NOT NULL | Préstamo relacionado. |
| version_plan | SMALLINT | NOT NULL | Versión correlativa; inicia en 1. |
| origen_plan | VARCHAR(25) | CHECK, NOT NULL | ORIGINAL o ANTICIPO_PARCIAL. |
| vigente | BOOLEAN | NOT NULL | Solo una versión vigente por préstamo. |
| id_liquidacion_origen | BIGINT | FK liquidaciones_anticipadas, NULL | Liquidación que originó nueva versión. |
| fecha_generacion | TIMESTAMP | NOT NULL | Momento de generación. |
| generado_por | BIGINT | FK usuarios, NOT NULL | Usuario/contexto que solicitó generación. |

## Tabla 14. cuotas

Detalle de cada plan. VENCIDA no se almacena como estado de pago; se calcula por fecha y saldo.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_cuota | BIGINT | PK | Identificador. |
| id_plan_pago | BIGINT | FK planes_pago, NOT NULL | Plan al que pertenece. |
| numero_cuota | SMALLINT | NOT NULL | Número dentro del plan. |
| fecha_vencimiento | DATE | NOT NULL | Fecha límite real, con el corrimiento de RN-19 (feriado/fin de semana); la que ve el cliente y la que usa el cálculo de mora (§6.4). |
| fecha_vencimiento_teorica | DATE | NOT NULL | *(D-23, AVANCE.md)* Fecha límite sin el corrimiento de RN-19. Solo se usa para prorratear el interés en cancelación total/anticipo parcial (§6.6/6.7), para que un feriado no distorsione el prorrateo. |
| capital_programado | NUMERIC(18,0) | NOT NULL | Capital programado. |
| interes_programado | NUMERIC(18,0) | NOT NULL | Interés compensatorio. |
| tributos_programados | NUMERIC(18,0) | NOT NULL | Tributos/conceptos programados. |
| total_programado | NUMERIC(18,0) | NOT NULL | Total cuota. |
| saldo_capital_posterior | NUMERIC(18,0) | NOT NULL | Saldo de capital según plan. |
| estado_pago | VARCHAR(20) | CHECK, NOT NULL | PENDIENTE, PARCIAL o PAGADA. |

## Tabla 15. liquidaciones_anticipadas

Snapshot del cálculo utilizado para anticipo parcial o cancelación total.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_liquidacion | BIGINT | PK | Identificador. |
| id_prestamo | BIGINT | FK prestamos, NOT NULL | Préstamo. |
| tipo_liquidacion | VARCHAR(20) | CHECK, NOT NULL | PARCIAL o TOTAL. |
| fecha_calculo | TIMESTAMP | NOT NULL | Momento exacto del cálculo. |
| saldo_capital_fecha | NUMERIC(18,0) | NOT NULL | Capital exigible. |
| interes_devengado | NUMERIC(18,0) | NOT NULL | Interés devengado. |
| tributos_aplicables | NUMERIC(18,0) | NOT NULL | Tributos aplicables. |
| interes_moratorio | NUMERIC(18,0) | NOT NULL | Moratorio a la fecha. |
| interes_punitorio | NUMERIC(18,0) | NOT NULL | Punitorio a la fecha. |
| monto_total_liquidacion | NUMERIC(18,0) | NOT NULL | Importe total exigible. |
| monto_anticipo_capital | NUMERIC(18,0) | NULL | Capital extraordinario en liquidación parcial. |
| nuevo_saldo_capital | NUMERIC(18,0) | NULL | Saldo posterior proyectado. |
| estado_liquidacion | VARCHAR(20) | CHECK, NOT NULL | GENERADA, APLICADA o CANCELADA. |
| calculado_por | BIGINT | FK usuarios, NOT NULL | Usuario solicitante. |
| fecha_aplicacion | TIMESTAMP | NULL | Momento de aplicación real. |
| id_archivo_liquidacion | BIGINT | FK archivos_binarios, NULL | PDF Jasper si se conserva/entrega formalmente. |

## Tabla 16. sesiones_caja

Sesiones de la única caja física. Solo una puede estar ABIERTA en todo el sistema.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_sesion_caja | BIGINT | PK | Identificador. |
| id_cajero | BIGINT | FK usuarios, NOT NULL | Cajero responsable. |
| fecha_hora_apertura | TIMESTAMP | NOT NULL | Inicio. |
| saldo_inicial | NUMERIC(18,0) | NOT NULL | Efectivo inicial. |
| fecha_hora_cierre | TIMESTAMP | NULL | Cierre. |
| saldo_teorico_cierre | NUMERIC(18,0) | NULL | Snapshot del saldo teórico al cerrar. |
| saldo_fisico | NUMERIC(18,0) | NULL | Efectivo contado. |
| diferencia_arqueo | NUMERIC(18,0) | NULL | Físico menos teórico. |
| estado_sesion | VARCHAR(20) | CHECK, NOT NULL | ABIERTA o CERRADA. |
| id_archivo_cierre | BIGINT | FK archivos_binarios, NULL | Comprobante Jasper de cierre cuando se conserva. |

## Tabla 17. desembolsos

Entrega física de fondos. Puede existir historial de un desembolso revertido y uno posterior válido; solo uno puede permanecer CONFIRMADO por préstamo.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_desembolso | BIGINT | PK | Identificador. |
| identificador_confirmacion | VARCHAR(60) | UNIQUE, NOT NULL | Clave de idempotencia. |
| id_prestamo | BIGINT | FK prestamos, NOT NULL | Préstamo desembolsado. |
| id_sesion_caja | BIGINT | FK sesiones_caja, NOT NULL | Caja ABIERTA utilizada. |
| monto_aprobado_snapshot | NUMERIC(18,0) | NOT NULL | Monto aprobado mostrado al desembolsar. |
| monto_deducciones | NUMERIC(18,0) | NOT NULL | Suma de cargos que descuentan del efectivo. Siempre fue 0 (RN-17: sin comisiones ni gastos en V1); eliminada en la ronda de columnas sin uso (18/10/2026). |
| monto_neto_entregado | NUMERIC(18,0) | NOT NULL | Efectivo entregado. |
| fecha_hora_desembolso | TIMESTAMP | NOT NULL | Momento de confirmación. |
| desembolsado_por | BIGINT | FK usuarios, NOT NULL | Cajero. |
| estado_desembolso | VARCHAR(20) | CHECK, NOT NULL | CONFIRMADO o REVERTIDO. |
| id_archivo_comprobante | BIGINT | FK archivos_binarios, NULL | Comprobante Jasper histórico. |

## Tabla 18. reversiones_desembolso

Reversión autorizada de un desembolso. No elimina ni modifica montos del movimiento original.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_reversion_desembolso | BIGINT | PK | Identificador. |
| identificador_confirmacion | VARCHAR(60) | UNIQUE, NOT NULL | Clave de idempotencia de la reversión. |
| id_desembolso | BIGINT | UNIQUE, FK desembolsos | Máximo una reversión por desembolso. |
| motivo_reversion | VARCHAR(500) | NOT NULL | Motivo obligatorio. |
| fecha_hora_reversion | TIMESTAMP | NOT NULL | Momento de reversión. |
| autorizado_por | BIGINT | FK usuarios, NOT NULL | Gerente/Administrador. |
| procesado_por | BIGINT | FK usuarios, NOT NULL | Cajero que ejecuta. |
| id_sesion_caja | BIGINT | FK sesiones_caja, NOT NULL | Caja ABIERTA actual. |
| monto_ingreso_caja | NUMERIC(18,0) | NOT NULL | Efectivo compensatorio que vuelve a caja. |
| id_archivo_comprobante | BIGINT | FK archivos_binarios, NULL | Comprobante Jasper de reversión. |

## Tabla 19. pagos

Cabecera de cada cobro confirmado. Todas las operaciones son en efectivo.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_pago | BIGINT | PK | Identificador. |
| identificador_confirmacion | VARCHAR(60) | UNIQUE, NOT NULL | Clave de idempotencia. |
| id_prestamo | BIGINT | FK prestamos, NOT NULL | Préstamo pagado. |
| id_sesion_caja | BIGINT | FK sesiones_caja, NOT NULL | Caja ABIERTA. |
| id_liquidacion_anticipada | BIGINT | FK liquidaciones_anticipadas, NULL | Liquidación utilizada en anticipo/cancelación. |
| numero_recibo | VARCHAR(50) | UNIQUE, NOT NULL | Número irrepetible. |
| fecha_hora_pago | TIMESTAMP | NOT NULL | Momento de confirmación. |
| monto_recibido | NUMERIC(18,0) | NOT NULL | Efectivo entregado por cliente. |
| monto_vuelto | NUMERIC(18,0) | NOT NULL DEFAULT 0 | Vuelto entregado. |
| monto_aplicado | NUMERIC(18,0) | NOT NULL | Monto aplicado a deuda; recibido - vuelto. |
| estado_pago | VARCHAR(20) | CHECK, NOT NULL | CONFIRMADO o REVERTIDO. |
| registrado_por | BIGINT | FK usuarios, NOT NULL | Cajero. |
| id_archivo_recibo | BIGINT | FK archivos_binarios, NULL | Recibo Jasper histórico. |

## Tabla 20. aplicaciones_pago

Distribución detallada de cada pago. Soporta cuotas normales y anticipo extraordinario de capital sin fingir una cuota futura.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_aplicacion_pago | BIGINT | PK | Identificador. |
| id_pago | BIGINT | FK pagos, NOT NULL | Pago origen. |
| tipo_aplicacion | VARCHAR(25) | CHECK, NOT NULL | CUOTA o ANTICIPO_CAPITAL. |
| id_cuota | BIGINT | FK cuotas, NULL | Obligatoria para CUOTA; NULL para ANTICIPO_CAPITAL. |
| monto_moratorio | NUMERIC(18,0) | NOT NULL DEFAULT 0 | Aplicado a moratorio. |
| monto_punitorio | NUMERIC(18,0) | NOT NULL DEFAULT 0 | Aplicado a punitorio. |
| monto_interes | NUMERIC(18,0) | NOT NULL DEFAULT 0 | Aplicado a interés compensatorio. |
| monto_tributos | NUMERIC(18,0) | NOT NULL DEFAULT 0 | Aplicado a tributos. |
| monto_capital | NUMERIC(18,0) | NOT NULL DEFAULT 0 | Aplicado a capital. |
| subtotal_aplicado | NUMERIC(18,0) | NOT NULL | Suma de conceptos de la aplicación. |

## Tabla 21. reversiones_pago

Reversión autorizada de un cobro. El pago original permanece trazable y la salida compensatoria se registra en caja actual.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_reversion_pago | BIGINT | PK | Identificador. |
| identificador_confirmacion | VARCHAR(60) | UNIQUE, NOT NULL | Clave de idempotencia. |
| id_pago | BIGINT | UNIQUE, FK pagos | Máximo una reversión por pago. |
| motivo_reversion | VARCHAR(500) | NOT NULL | Motivo obligatorio. |
| fecha_hora_reversion | TIMESTAMP | NOT NULL | Momento de reversión. |
| autorizado_por | BIGINT | FK usuarios, NOT NULL | Gerente/Administrador. |
| procesado_por | BIGINT | FK usuarios, NOT NULL | Cajero que ejecuta. |
| id_sesion_caja | BIGINT | FK sesiones_caja, NOT NULL | Caja ABIERTA actual. |
| monto_salida_caja | NUMERIC(18,0) | NOT NULL | Efectivo compensatorio que sale de caja. |
| id_archivo_comprobante | BIGINT | FK archivos_binarios, NULL | Comprobante Jasper de reversión. |

## Tabla 22. auditoria_operaciones

Bitácora transversal de solo inserción para reconstruir quién hizo qué y cuándo.

| **Campo** | **Tipo PostgreSQL** | **Restricción** | **Descripción / regla** |
|---|---|---|---|
| id_auditoria | BIGINT | PK | Identificador. |
| id_usuario | BIGINT | FK usuarios, NOT NULL | Usuario responsable. |
| fecha_hora | TIMESTAMP | NOT NULL | Momento exacto. |
| operacion | VARCHAR(80) | NOT NULL | Ej. APROBAR_SOLICITUD, PROCESAR_COBRO. |
| tipo_entidad | VARCHAR(60) | NOT NULL | PERSONA, SOLICITUD, PRESTAMO, PAGO, etc. |
| id_entidad | BIGINT | NOT NULL | Identificador lógico de la entidad. |
| valor_anterior | JSONB | NULL | Snapshot anterior cuando corresponda. |
| valor_nuevo | JSONB | NULL | Snapshot posterior cuando corresponda. |
| direccion_ip | VARCHAR(64) | NULL | IP de origen si está disponible. |
| detalle | TEXT | NULL | Información adicional no sensible. |

# 8.7. Relaciones y cardinalidades principales

| **Relación** | **Regla** |
|---|---|
| personas -\> solicitudes_credito | 1:N como cliente. |
| solicitudes_credito -\> prestamos | 1:0..1. Solo una solicitud aprobada/formalizada origina un préstamo. |
| prestamos -\> cargos_operacion | 1:N. Desglose de conceptos autorizados de la operación. |
| prestamos -\> planes_pago | 1:N histórico; solo una versión puede estar vigente. |
| planes_pago -\> cuotas | 1:N. Las cuotas pertenecen a una versión de plan concreta. |
| prestamos -\> liquidaciones_anticipadas | 1:N. Se conserva cada cálculo utilizado o cancelado. |
| prestamos -\> desembolsos | 1:N histórico; máximo un desembolso en estado CONFIRMADO por vez. |
| desembolsos -\> reversiones_desembolso | 1:0..1. |
| prestamos -\> pagos | 1:N. |
| pagos -\> aplicaciones_pago | 1:N. |
| cuotas -\> aplicaciones_pago | 1:N para aplicaciones de tipo CUOTA. |
| pagos -\> reversiones_pago | 1:0..1. |
| archivos_binarios -\> documentos_expediente | 1:N potencial por referencia, aunque normalmente cada documento conserva su propio archivo. |
| documentos_expediente -\> consentimientos_crediticios | 1:N como evidencia cuando corresponda. |
| consentimientos_crediticios -\> consultas_crediticias | 1:N; cada consulta tiene consentimiento obligatorio. |
| sesiones_caja -\> desembolsos/pagos/reversiones | 1:N. Toda operación monetaria referencia una caja ABIERTA al confirmarse. |
| usuarios -\> entidades operativas/auditoría | 1:N. Toda acción crítica conserva usuario responsable. |

# 8.8. Restricciones, índices y controles PostgreSQL obligatorios

| **Importante.** La lógica de negocio vive en Servicios Java, pero PostgreSQL debe reforzar invariantes simples que puedan expresarse sin duplicar fórmulas. Las restricciones de base no sustituyen las validaciones del servidor. |
|---|

- UNIQUE en usuarios.nombre_usuario, usuarios.cedula_identidad, personas.cedula_identidad, solicitudes_credito.codigo_solicitud, prestamos.identificador_operacion, prestamos.numero_pagare, pagos.numero_recibo y claves de idempotencia.

- CHECK id_cliente \<\> id_codeudor cuando exista codeudor (sin uso desde el retiro de codeudor, 18/10/2026 — pendiente de eliminar junto con la columna en el Commit 3 del refactor).

- Índice único parcial para una sola solicitud abierta por cliente.

- La regla de un solo préstamo no cerrado por cliente se validará transaccionalmente en Servicios antes de formalizar, bloqueando/consultando la ficha del cliente para evitar carreras. No se duplicará id_cliente en prestamos únicamente para forzar un índice que rompería la normalización.

- CHECK en documentos_expediente para exigir al menos un contexto no nulo entre id_persona, id_solicitud o id_prestamo.

- Índice único parcial para una sola sesión de caja ABIERTA en todo el sistema.

- Índice único parcial para una sola versión de plan vigente por préstamo.

- Índice único parcial para un solo desembolso CONFIRMADO por préstamo; un desembolso REVERTIDO permanece histórico y permite, si el flujo funcional lo habilita, registrar posteriormente otro desembolso válido.

- UNIQUE en reversiones_pago.id_pago y reversiones_desembolso.id_desembolso para impedir doble reversión.

- CHECK en pagos para que monto_recibido = monto_aplicado + monto_vuelto y todos los importes sean no negativos.

- CHECK en aplicaciones_pago: tipo CUOTA exige id_cuota; ANTICIPO_CAPITAL exige id_cuota NULL. subtotal_aplicado debe igualar la suma de conceptos.

- Índices por estados/fechas de solicitudes, préstamos, cuotas, pagos y auditoría para soportar bandejas y reportes sin indexar indiscriminadamente todas las columnas.

- FK de información histórica con política RESTRICT/NO ACTION. La aplicación no realizará borrado físico de préstamos, pagos, desembolsos, documentos ni auditoría.

## Ejemplos de índices parciales PostgreSQL

```

```

# 8.9. Fronteras transaccionales e idempotencia

- Aprobación: decisión, snapshot de condiciones y auditoría se confirman juntas.

- Formalización: creación del préstamo, cargos, plan original, documentos generados que deban persistirse y auditoría se confirman de forma coherente. La habilitación PENDIENTE_DESEMBOLSO solo ocurre cuando el checklist documental está completo.

- Desembolso: validación de caja, inserción del desembolso, efecto de caja, cambio de estado del préstamo y auditoría forman una única transacción lógica.

- Saldo teórico de caja: saldo_inicial + pagos CONFIRMADOS netos (monto_recibido - monto_vuelto) - desembolsos CONFIRMADOS + reversiones_desembolso de la sesión - reversiones_pago de la sesión. Al cerrar se conserva saldo_teorico_cierre como snapshot inmutable.

- Cobro: pago, aplicaciones, actualización de estados de cuota/préstamo, efecto neto en caja, recibo histórico y auditoría se confirman juntos o no se confirma nada.

- Anticipo parcial: pago, aplicación extraordinaria a capital, liquidación utilizada, cierre de plan anterior, creación de nueva versión del plan y auditoría deben quedar coordinados transaccionalmente.

- Reversión: registro de reversión, cambio de estado del original a REVERTIDO, restitución de saldos/estados, movimiento compensatorio en caja ABIERTA actual, comprobante y auditoría se confirman juntos.

- Las claves identificador_confirmacion deben mantenerse UNIQUE para que doble clic, reintento o recuperación de conexión no produzcan dos operaciones válidas.

- Los DAO utilizados dentro de una misma operación crítica deberán compartir la misma conexión/transacción JDBC; ningún DAO hará commit independiente en mitad de un caso de uso financiero.

# 8.10. Almacenamiento documental y JasperReports

- archivos_binarios es la fuente física oficial de todos los archivos conservados. El campo contenido BYTEA viaja dentro del respaldo PostgreSQL.

- documentos_expediente aporta significado y contexto: cédula, comprobante de ingreso, consentimiento, contrato generado, contrato firmado, pagaré, plan de pagos y otros documentos del expediente.

- Recibos, comprobantes de desembolso, reversión, liquidación y cierre de caja pueden referenciar directamente su archivo binario desde la transacción que los originó.

- Las plantillas .jrxml/.jasper de JasperReports 6.20.6 forman parte del WAR. El PDF histórico generado que deba conservarse se guarda como archivo_binario con origen_archivo = JASPER.

- Un documento contractual ya emitido no se reconstruirá en el futuro usando una plantilla nueva para reemplazar el original. Se reimprime/consulta el PDF histórico guardado.

- Los formatos admitidos para cargas de expediente son PDF, JPG/JPEG y PNG en la V1. El tamaño máximo por archivo se controla mediante parametro_sistema y se valida antes de persistir el BYTEA.

- Los reportes ad hoc regenerables no se almacenarán automáticamente como BYTEA salvo que una regla funcional exija conservar esa salida como documento histórico.

- No se almacenarán rutas absolutas, rutas de escritorio, carpetas compartidas ni directorios de Tomcat como ubicación oficial de documentos de negocio.

| **Portabilidad documental.** Al restaurar la base PostgreSQL del cliente, se restauran también cédulas, comprobantes, contratos, pagarés, recibos y demás archivos históricos. La aplicación no depende de copiar una carpeta documental separada. |
|---|

# 8.11. Datos históricos, snapshots y valores derivados

- Actualizar personas.ingreso_neto_comprobado no modifica ingreso_cliente_evaluado de solicitudes anteriores (ingreso_codeudor_evaluado sin uso desde el retiro de codeudor, 18/10/2026).

- Cambiar parámetros, límites o datos institucionales no modifica condiciones almacenadas en solicitudes/préstamos ni PDFs históricos ya emitidos.

- Los planes sustituidos por anticipo parcial permanecen en planes_pago con vigente = FALSE; sus cuotas no se borran.

- Los pagos y desembolsos revertidos permanecen en sus tablas con estado REVERTIDO y con su registro de reversión asociado; sus montos originales no se sobrescriben.

- La condición VENCIDA de una cuota se deriva: fecha_vencimiento anterior a la fecha de cálculo y saldo exigible pendiente. No se almacena como un estado que compita con PARCIAL.

- Los saldos operativos que puedan derivarse del detalle no deberán duplicarse sin necesidad. Cuando se conserve un snapshot (por ejemplo saldo_teorico_cierre), su finalidad histórica debe estar documentada.

# 8.12. Respaldo, restauración y traslado al servidor del cliente

- El respaldo PostgreSQL incluirá datos estructurados y BYTEA de documentos. No existirá un repositorio de archivos externo indispensable para reconstruir la operación.

- La instalación operativa del cliente se compone del archivo WAR desplegado en Tomcat 9 y una base PostgreSQL restaurada con sus documentos. Las credenciales/conexión del entorno se configurarán para el Tomcat del cliente conforme al Documento 09.

- El proyecto de desarrollo conservará scripts SQL versionados para reproducibilidad y mantenimiento, aunque la entrega operativa al cliente pueda realizarse mediante copia/restauración de la base.

- Las pruebas de restauración deberán comprobar no solo tablas y saldos, sino también apertura de archivos BYTEA y reimpresión de documentos históricos.

- Los backups deben respetar el RPO/RTO definidos en RNF16 (Respaldo) -- corregido: decía RNF-09, que en `docs/RF_ANTEPROYECTO.md` es el de bloqueo de cuenta/mensaje único, no respaldo.

# 8.13. Exclusiones deliberadas de la versión inicial

- No se modelan sucursales, centros de costo ni múltiples cajas físicas.

- No se modela multimoneda; la V1 utiliza PYG.

- No existe tabla de medios de pago porque la V1 opera exclusivamente en efectivo.

- No se modela conciliación bancaria, transferencias, cheques ni SIPAP.

- No se modela cobranza judicial, call center, geolocalización ni promesas de pago masivas.

- No se crean tablas duplicadas exclusivamente para Data-OCD, DNIT o SIRO; las exportaciones se producen desde la información normalizada disponible.

- No se incorpora una matriz compleja de permisos: existen tres perfiles fijos y las autorizaciones se controlan en el servidor/Servicios.

- No se almacenan archivos fuera de PostgreSQL como dependencia operativa del expediente.

# 8.14. Criterios de aceptación del modelo

| **Criterio** | **Condición de aprobación** |
|---|---|
| Normalización | Las entidades principales no duplican personas, planes, archivos o detalles de pago; cabeceras y detalles están separados coherentemente. |
| Documentos | Todos los archivos históricos pueden reconstruirse desde PostgreSQL y no dependen de rutas locales. |
| Caja | Solo efectivo; una sola caja ABIERTA; sesiones cerradas inmutables; pagos/desembolsos/reversiones referencian sesión. |
| Estados | Solicitud, préstamo, cuota, pago y desembolso usan los estados oficiales de los Documentos 01 a 07. |
| Anticipos | El modelo soporta liquidación, aplicación extraordinaria a capital y nueva versión de plan sin sobrescribir histórico. |
| Reversiones | Cobros y desembolsos conservan original y máximo una reversión autorizada por movimiento. |
| Idempotencia | Claves únicas impiden operaciones duplicadas por doble clic o reintento. |
| Portabilidad | Una restauración PostgreSQL + WAR permite reconstruir la operación del cliente sin carpeta documental externa. |
| Trazabilidad | Toda operación crítica puede relacionarse con usuario, fecha/hora, entidad y auditoría. |
| Desarrollo | Documento 09, código Java y scripts SQL deberán utilizar estos nombres y relaciones sin estructuras paralelas no aprobadas. |

| **Estado del Documento 08.** MODELO RELACIONAL Y DICCIONARIO DE DATOS FINAL PARA POSTGRESQL. Este documento reemplaza la versión anterior y queda aprobado como base técnica del esquema SQL, DAO/Repositorios, Servicios, JasperReports y pruebas de persistencia de la V1. |
|---|
