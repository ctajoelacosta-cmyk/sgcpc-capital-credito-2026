# Inventario de campos — SGCPC (PASO 2, D-124)

Documento de solo lectura, a pedido de Joel (29/09/2026), antes del PASO 3 (parámetro de garante/codeudor). Sin ningún cambio de código. Dos partes: (1) todo lo que el sistema captura hoy en persona, solicitud, documentos y consentimientos; (2) comparación contra lo que pide una financiera paraguaya en la entrevista presencial, con esfuerzo estimado para cada falta.

## 1. Campos capturados hoy

### 1.1. Persona

Tabla `personas` (`bd/01_esquema.sql:92-117`); formulario en `clientes/ficha.jsp`; validado en `ServicioClientes.validarDatosPersona` (`ServicioClientes.java:56-78`).

| Campo en pantalla | Obligatorio | Tabla.columna |
|---|---|---|
| Cédula de identidad | Sí (solo números; única en el sistema) | `personas.cedula_identidad` |
| RUC | No | `personas.ruc` |
| Nombres | Sí | `personas.nombres` |
| Apellidos | Sí | `personas.apellidos` |
| Fecha de nacimiento | Sí (no puede ser futura; **sin edad mínima ni máxima**) | `personas.fecha_nacimiento` |
| Estado civil | Sí (`<select>` con 5 opciones: Soltero/a, Casado/a, Unión de hecho, Divorciado/a, Viudo/a — texto libre en la base, no una lista controlada) | `personas.estado_civil` |
| Domicilio | Sí (un solo campo de texto libre) | `personas.domicilio` |
| Dirección laboral | No (texto libre) | `personas.direccion_laboral` |
| Lugar de trabajo | No (texto libre, sin lista controlada) | `personas.lugar_trabajo` |
| Teléfono / WhatsApp | Sí | `personas.telefono_whatsapp` |
| Teléfono alternativo | No | `personas.telefono_alternativo` |
| Correo electrónico | No | `personas.correo_electronico` |
| Ingreso neto comprobado | Sí (no negativo) | `personas.ingreso_neto_comprobado` |
| Presenta comprobante de ingreso | Casilla, no obligatoria | `personas.presenta_comprobante_ingreso` |
| Referencia — nombre | No | `personas.referencia_nombre` |
| Referencia — teléfono | No | `personas.referencia_telefono` |
| Otras referencias | No (texto libre) | `personas.referencias` |

No capturados hoy en `Persona`: fecha de vencimiento de cédula, datos del cónyuge, domicilio por partes, tipo/antigüedad de vivienda, RUC/dirección/teléfono/cargo/antigüedad del empleador, otros ingresos, gastos fijos, deudas con otras entidades, PEP, origen de fondos, segunda referencia estructurada.

### 1.2. Solicitud de crédito

Tabla `solicitudes_credito` (`bd/01_esquema.sql:216-256`); formulario en `solicitudes/nueva.jsp`; validado en `ServicioSolicitudes.java`.

| Campo en pantalla | Obligatorio | Tabla.columna |
|---|---|---|
| Cédula del cliente | Sí | `solicitudes_credito.id_cliente` (resuelto por cédula) |
| Monto solicitado | Sí | `solicitudes_credito.monto_solicitado` |
| Plazo (meses) | Sí | `solicitudes_credito.plazo_solicitado_meses` |
| Destino del crédito | Sí (texto libre, valida solo "no vacío") | `solicitudes_credito.destino_credito` |
| Primera fecha de vencimiento | Sí | `solicitudes_credito.primera_fecha_vencimiento_solicitada` |
| Motivo de rechazo | Obligatorio solo al rechazar; en pantalla es una lista (`aprobaciones/ficha.jsp:178-184`: "Informconf: figura con deudas", "Capacidad de pago insuficiente", "Documentación incompleta o inconsistente", "Otro" + detalle) pero se guarda como texto libre, no un valor fijo | `solicitudes_credito.motivo_rechazo` (TEXT) |
| Motivo de cancelación | Obligatorio solo al cancelar (texto libre) | `solicitudes_credito.motivo_cancelacion` |

El resto de las columnas de `solicitudes_credito` (tasas, cuota simulada, ingresos evaluados, límite de afectación aplicado, estado) las calcula el motor financiero — no son campos que alguien tipee.

### 1.3. Documentos del expediente

Tablas `tipos_documento`, `documentos_expediente`, `archivos_binarios`. Tipos ya sembrados en `bd/03_parametros.sql:160-180`:

| Código | Nombre en pantalla | Obligatorio para desembolsar |
|---|---|---|
| `CEDULA_IDENTIDAD` | Cédula de identidad | No |
| `COMPROBANTE_INGRESO` | Comprobante de ingreso | No |
| `COMPROBANTE_DOMICILIO` | Comprobante de domicilio | No |
| `AUTORIZACION_CONSULTA_BURO` | Autorización de consulta al buró | No |
| `RESULTADO_CONSULTA_BURO` | Resultado de la consulta al buró | No |
| `CONTRATO_GENERADO` / `CONTRATO_FIRMADO` | Contrato (generado / firmado) | Solo el firmado |
| `PAGARE_GENERADO` / `PAGARE_FIRMADO` | Pagaré (generado / firmado) | Solo el firmado |
| `PLAN_PAGOS_GENERADO` | Plan de pagos (generado) | No |
| `DEVOLUCION_PAGARE_GENERADO` / `_FIRMADO` | Constancia de devolución (generada / firmada) | — (cierre documental) |

El formulario de carga (`ControladorDocumentosServlet`) pide: persona o préstamo, tipo de documento (de la lista de arriba) y el archivo — ningún metadato adicional (sin fecha propia del documento más que la de carga, sin número de comprobante).

**Comprobante de ingreso es un solo tipo genérico** — hoy no distingue entre liquidación de sueldo, extracto de IPS, constancia de jubilación, etc. **Comprobante de domicilio ya existe como tipo genérico** — no está pensado como "factura de ANDE/ESSAP/telefonía" específicamente, pero serviría para eso tal cual. Cédula es un solo tipo (no anverso y reverso por separado).

### 1.4. Consentimientos y consulta crediticia

Tabla `consentimientos_crediticios` (`bd/01_esquema.sql:178-187`) y `consultas_crediticias`.

| Campo en pantalla | Obligatorio | Tabla.columna |
|---|---|---|
| Documento de autorización (evidencia) | Sí | `consentimientos_crediticios.id_documento_evidencia` |
| Finalidad | Se manda fija ("Evaluación de solicitud de crédito"), texto libre en la base | `consentimientos_crediticios.finalidad` |
| Proveedor del buró | Sí (texto, valor por defecto "Informconf") | `consultas_crediticias.proveedor_buro` |
| Puntaje obtenido | No | `consultas_crediticias.puntaje_obtenido` |
| Clasificación obtenida | No | `consultas_crediticias.clasificacion_obtenida` |
| ¿Tiene mora? | Sí | `consultas_crediticias.indicador_mora` |
| Resumen del resultado | No | `consultas_crediticias.resumen_resultado` |
| Documento de resultado | Sí | `consultas_crediticias.id_documento_resultado` |

**La `finalidad` del consentimiento siempre es la del buró crediticio — no existe un consentimiento separado para publicidad.**

## 2. Comparación contra la entrevista presencial de una financiera paraguaya

| # | Ítem pedido | Estado | Detalle | Esfuerzo |
|---|---|---|---|---|
| 1 | Fecha de vencimiento de la cédula | **Falta** | No hay columna ni campo. | **Bajo** — 1 columna (`personas.cedula_vencimiento DATE NULL`), 1 campo en el formulario, validación opcional "no vencida". |
| 2 | Estado civil y datos del cónyuge | **Parcial** | Estado civil ya existe (texto libre, podría pasar a lista controlada). Datos del cónyuge (nombre, cédula) no existen. | **Medio** — 2 columnas nuevas condicionales (mostrar solo si Casado/a o Unión de hecho), ajustar el JSP para mostrarlas/ocultarlas con JS. |
| 3 | Domicilio por partes (departamento/ciudad/barrio/referencia), tipo de vivienda, antigüedad | **Falta** | Hoy es un solo campo de texto libre. | **Medio** — reemplazar 1 columna por 4-5 (o agregar las nuevas y dejar `domicilio` como el texto combinado para impresos), + tipo de vivienda (lista corta) + antigüedad (número de años). Impacto en cualquier PDF/pantalla que hoy arma la dirección a partir de `domicilio` tal cual (contrato, pagaré, facturas) — hay que decidir si se sigue mostrando como una línea combinada o por partes. |
| 4 | Situación laboral en lista + empleador (RUC, dirección, teléfono, cargo, antigüedad) | **Falta** | Solo existen `lugar_trabajo` y `direccion_laboral`, ambos texto libre, sin lista controlada ni el resto de los campos. | **Alto** — nueva lista controlada (8 valores que dio Joel), 5-6 columnas nuevas (RUC empleador, dirección/teléfono laboral ya existe uno, cargo, antigüedad laboral), y esta lista es la que después decide qué documentos pedir (ítem 10) y qué situaciones admite la política de crédito (ítem 12) — conviene diseñarla junto con esos dos puntos, no aislada. |
| 5 | Ingreso, otros ingresos, gastos fijos, deudas con otras entidades | **Falta** | Solo existe `ingreso_neto_comprobado`. | **Alto** — no es solo agregar columnas: si "otros ingresos" suma y "gastos fijos"/"deudas" restan, hay que decidir la fórmula nueva de capacidad de pago con el motor financiero (`ServicioMotorFinanciero.calcularCapacidadPago`) — es una regla de negocio nueva (necesitaría su propio D-NN), no un cambio de pantalla. |
| 6 | Destino del crédito | **Existe** | `solicitudes_credito.destino_credito`, texto libre, ya obligatorio. | — |
| 7 | Dos referencias personales | **Parcial** | Existe una referencia estructurada (`referencia_nombre`/`referencia_telefono`) más un campo libre "otras referencias". | **Medio** — la forma prolija es una tabla `referencias_personales` (nombre, teléfono, relación, N filas) en vez de duplicar columnas; si se prefiere lo mínimo, agregar un segundo par de columnas es **Bajo**. |
| 8 | Declaración PEP y origen de fondos (SEPRELAD) | **Falta** | No existe ningún campo. SEPRELAD solo se menciona en Doc. 00/10 como un reporte futuro "a pedido", nunca como dato capturado. | **Alto** — es un requisito regulatorio real: casilla PEP + detalle si aplica, campo de origen de fondos, y probablemente debería quedar auditado aparte (quién lo declaró, cuándo) — más que un campo, es una sección nueva con su propia validación. |
| 9 | Consentimiento de publicidad, separado del crediticio | **Falta** | `consentimientos_crediticios.finalidad` siempre es la del buró; no hay un segundo consentimiento. | **Bajo-Medio** — agregar `tipo_consentimiento` (`CREDITICIO`/`PUBLICIDAD`) a la tabla existente, o una tabla separada si se prefiere no mezclar los dos flujos; un checkbox nuevo en el alta/ficha del cliente. |
| 10 | Documentos según situación laboral (cédula ambos lados, liquidación/extracto IPS 3-6 meses, constancia de jubilación, factura ANDE/ESSAP/telefonía) | **Falta** | `COMPROBANTE_INGRESO` es un solo tipo genérico; cédula es un solo tipo (no anverso/reverso); `COMPROBANTE_DOMICILIO` ya existe y serviría para la factura de servicios tal cual. | **Medio** — nuevos tipos en `tipos_documento` (son solo filas, el modelo ya lo soporta) + lógica que decida qué tipos exigir según la situación laboral (depende del ítem 4). Sin el ítem 4, esto queda en "más tipos de documento" sin poder exigirlos automáticamente. |
| 11 | Motivos de rechazo en lista | **Parcial** | Ya hay una lista en la pantalla (`aprobaciones/ficha.jsp`), pero se guarda como texto libre (`motivo_rechazo` TEXT), no como un valor fijo verificable. | **Bajo** — agregar una tabla `motivos_rechazo` (o un `CHECK` con los valores fijos) + columna `id_motivo_rechazo`, dejando el texto libre actual como "detalle" adicional cuando el motivo es "Otro". |
| 12 | Política de crédito parametrizable (edad mín/máx, zonas habilitadas, situaciones laborales admitidas) | **Falta** | Ninguna validación de edad, ninguna zona geográfica, ninguna lista de situaciones admitidas en `parametros_sistema`. | **Alto** — 2 parámetros simples (edad mínima/máxima) son fáciles, pero "zonas habilitadas" depende de tener el domicilio por partes (ítem 3) y "situaciones admitidas" depende de la lista de situación laboral (ítem 4) — el diseño completo de este ítem conviene dejarlo para después de resolver esos dos. |

### Resumen de esfuerzo

- **Bajo, independientes entre sí**: 1 (vencimiento de cédula), 11 (motivos de rechazo a lista fija), la mitad de 9 (consentimiento de publicidad).
- **Medio**: 2 (cónyuge), 3 (domicilio por partes), 7 (segunda referencia), 10 (documentos por situación laboral, si el 4 ya existe).
- **Alto, y conviene decidirlos juntos por sus dependencias**: 4 (situación laboral) → 10 (documentos) → 12 (zonas/situaciones admitidas); y por separado, 5 (otros ingresos/gastos/deudas, toca el motor financiero) y 8 (PEP/SEPRELAD, requisito regulatorio con su propia sección).

Nada de esto está programado todavía — queda a la espera de que Joel apruebe qué entra y en qué orden, antes de tocar código.
