# Funcionamiento del sistema, por RF y por proceso

Índice de la documentación de `docs/funcionamiento/`. Cada documento describe, para un
requisito funcional (RF) o para un proceso que cruza varios RF, cómo funciona hoy (armado
desde el código real, no desde lo que se planeó), con sus archivos, sus decisiones y sus
pendientes.

**Recorrido operativo (desde el 21/10/2026)**: el sistema recorrido de punta a punta, en el
orden real de un día de la financiera, con la matriz RF → escena → cumple/observación, está en
[docs/recorrido/00_LEEME.md](../recorrido/00_LEEME.md) (reemplaza a `FLUJO_OPERATIVO.md`, que
queda retirado con un aviso de mudanza).

## Regla (desde el 10/10/2026)

1. **Antes de modificar algo, leer primero el documento del RF o del proceso que corresponde**
   (si existe). Si no existe, leer de todos modos `docs/00_especificacion_maestra.md` y
   `docs/RF_ANTEPROYECTO.md` para ese RF.
2. **Cada cambio de código actualiza ese documento en el mismo commit** — nunca un commit de
   código sin su actualización de documentación, cuando el RF/proceso ya tiene un documento.
3. **Cuando se cierra un RF** (queda probado y Joel da el visto bueno), **se crea su documento**
   si todavía no existe.
4. Ningún documento de esta carpeta dice con qué método o herramienta se programó o se
   escribió — el documento describe el sistema, no cómo se construyó.
5. **Todo análisis que se haga sobre un RF o un proceso se guarda en su documento** (sección
   "Análisis" de la plantilla, de abajo), completo y con fecha — no alcanza con dejarlo solo en
   una respuesta de la conversación.

## Plantilla obligatoria (desde el 10/10/2026)

Todo documento de `docs/funcionamiento/` (RF o proceso) tiene estas secciones, en este orden:

1. **Qué exige el anteproyecto** — el texto textual del RF y/o los RNF relacionados, tal como
   están en `docs/RF_ANTEPROYECTO.md`.
2. **Cómo funciona** — caso por caso: qué ve el usuario, qué pasa por dentro.
3. **Auditoría** — qué eventos registra y cuándo.
4. **Dónde está** — archivos (servicios, controladores, filtros, pantallas), tablas y
   parámetros.
5. **Dependencias** — de qué RF depende este, y a qué RF afecta un cambio acá.
6. **Análisis** — cada análisis que se haga sobre este RF/proceso, con fecha, completo (no un
   resumen: el análisis entero, tal como se hizo).
7. **Decisiones** — qué se decidió, por qué, y quién lo decidió.
8. **Historial de cambios** — fecha, qué cambió, commit y tag. Lo más nuevo arriba.
9. **Pruebas realizadas en pantalla** — qué se probó, cuándo, y el resultado.
10. **Pendientes y trabajo futuro**.
11. **Guion para la defensa**.

Esta carpeta es nueva: los RF que ya estaban cerrados antes del 10/10/2026 todavía no tienen
documento propio (se crea la primera vez que se vuelvan a tocar, por la regla 3). El estado de
la tabla de abajo se armó a partir del plan de etapas de `AVANCE.md` — si algún estado no es
exacto, avisar para corregirlo.

## RF01 a RF40 (`docs/RF_ANTEPROYECTO.md`)

**Dos estados posibles, que no significan lo mismo (corrección de Joel, 10/10/2026).**
**"Probado contra el anteproyecto"**: se revisó el RF punto por punto contra el texto firmado
(`docs/RF_ANTEPROYECTO.md`), con el análisis completo guardado en su documento y una prueba en
pantalla ya confirmada. **"Implementado (etapa E…), sin recorrido contra el anteproyecto"**: el
código funciona y quedó probado al cerrar su etapa (`AVANCE.md`), pero todavía no se hizo ese
repaso punto por punto contra la letra del RF — puede tener el mismo tipo de hallazgos que ya
aparecieron al revisar RF04/RF05/RF06 (detalles que no coinciden exactamente con el texto
firmado) sin que todavía se haya mirado.

**Resumen (14/10/2026): 40 de 40 RF consolidados** — probados en pantalla o contra el
anteproyecto. RF01, RF02 y RF03 (los únicos 3 que venían de una prueba informal previa a esta
serie de documentos) tuvieron su **reprueba formal el 14/10/2026**, perfil oficial, contra
`sgcpc_db_pruebas` — tags `rf01-reprobado`/`rf02-reprobado`/`rf03-reprobado`, ver cada
documento §9. RF21 (deuda exigible/mora/IVA) se probó el 13/10/2026 con una mora real
(`CC-2026-00027`, sin simular ningún reloj) — ver
[RF21_deuda_exigible.md](RF21_deuda_exigible.md) §9. Quedan, aparte, las pruebas por fecha de
RF04 (30 días)/RF16 (5 días)/incobrables (180 días) y los tramos de mora 61–90/91–180/">180"
sin datos todavía — en análisis, ver el mismo documento §10.

| RF | Módulo | Estado | Tag | Documento |
|---|---|---|---|---|
| RF01 | Clientes y solicitudes (simular préstamo) | **REPROBADO FORMALMENTE el 14/10/2026** (perfil oficial) — cuota/TNA/CFT exactos, "Comparar plazos", PDF con logo y glosario, rangos rechazados, sin registrar nada | `rf01-reprobado`, `rf01-probado`, `rf01-comparar-plazos` | [RF01_cotizador.md](RF01_cotizador.md) |
| RF02 | Clientes y solicitudes (alta de clientes) | **REPROBADO FORMALMENTE el 14/10/2026** (perfil oficial) — precalificación (75 y 35 años), "Faltan datos obligatorios", "Imprimir requisitos" con logo, alta con cédula con puntos aceptada | `rf02-reprobado`, `rf02-probado` | [RF02_clientes.md](RF02_clientes.md) |
| RF03 | Clientes y solicitudes (expediente de documentos) | **REPROBADO FORMALMENTE el 14/10/2026** (perfil oficial) — expediente de 4 documentos, autorización AUT-2026-00066, cédula vencida con aviso en vivo y renovación | `rf03-reprobado`, `rf03-probado`, `rf03-agujero-cerrado`, `rf03-situacion-laboral` | [RF03_expediente.md](RF03_expediente.md) |
| RF04 | Clientes y solicitudes (autorización + consulta al buró) | Probado contra el anteproyecto, completo el 10/10/2026 | `rf04-probado` | [RF04_buro.md](RF04_buro.md) |
| RF05 | Clientes y solicitudes (solicitud, cuota, afectación) | Probado contra el anteproyecto, completo el 10/10/2026 (40 % rechazado al crear/simular, indicador en vivo, rango de fecha 20-45 días) | `rf05-probado` | [RF05_solicitud.md](RF05_solicitud.md) |
| RF06 | Clientes y solicitudes (enviar a aprobación / cancelar) | Probado contra el anteproyecto, completo el 10/10/2026 (revalidación con datos actuales, lista fija de motivos de cancelación, bug de auditoría corregido, glosario financiero en usted) | `rf06-probado` | [RF06_envio_aprobacion.md](RF06_envio_aprobacion.md) |
| RF07 | Aprobación (pendientes + historial) | Probado contra el anteproyecto, completo el 11/10/2026 (tiempo esperando, filtros y columnas de decisión en Historial, CANCELADA en el informe, checklist filtrado por estado, tarjeta del Panel en "Hoy") | `rf07-probado` | [RF07_bandeja_aprobacion.md](RF07_bandeja_aprobacion.md) |
| RF08 | Aprobación (resumen de la solicitud) | Probado contra el anteproyecto, completo el 11/10/2026 (franja de mora/riesgo alto arriba de todo, consulta más recientes por persona con re-vinculación al decidir, confirmación al registrar D/E sin mora, 10 textos en usted) | `rf08-probado` | [RF08_resumen_solicitud.md](RF08_resumen_solicitud.md) |
| RF09 | Aprobación (aprobar) | Probado contra el anteproyecto, completo el 11/10/2026 (carrera de dos pestañas cerrada, botón "Sí, aprobar" con foco en Cancelar con mora) | `rf09-probado` | [RF09_aprobar.md](RF09_aprobar.md) |
| RF10 | Aprobación (rechazar) | Probado contra el anteproyecto, completo el 11/10/2026 (lista de motivos reducida a 4, carrera de dos pestañas cerrada, botón "Sí, rechazar") | `rf10-probado` | [RF10_rechazar.md](RF10_rechazar.md) |
| RF11 | Formalización (fecha del primer vencimiento) | Probado contra el anteproyecto, completo el 11/10/2026 (rango tras corrimiento por fin de semana/feriado, CFT recalculado, 40 % revalidado, auditoría con el detalle completo) | `rf11-probado` | [RF11_formalizacion.md](RF11_formalizacion.md) |
| RF12 | Formalización (contrato/pagaré/plan en PDF) | Probado contra el anteproyecto, completo el 11/10/2026 (CC-2026-00001, número único en los 3 PDF, cifras idénticas a la pantalla) | `rf12-probado` | [RF12_documentos_pdf.md](RF12_documentos_pdf.md) |
| RF13 | Formalización (firmados + checklist) | Probado contra el anteproyecto, completo el 11/10/2026 (carga de firmados, 3 rechazos de archivo, bloqueo de regenerar, bandeja de la cajera en vivo) | `rf13-probado` | [RF13_firmados_checklist.md](RF13_firmados_checklist.md) |
| RF14 | Formalización (anular antes del desembolso) | Probado contra el anteproyecto, completo el 12/10/2026 (motivo mínimo de 10 caracteres rechazado en el servidor, franja con quién y cuándo, checklist corregido) — falta reprobar en pantalla el bloqueo del motivo corto con la próxima operación pendiente de desembolso (ver el documento, sección 9) | `rf14-probado` | [RF14_anular_prestamo.md](RF14_anular_prestamo.md) |
| RF15 | Desembolso y caja (apertura de caja) | Probado contra el anteproyecto, completo el 11/10/2026 (caja de un día anterior bloqueada en servidor, solo se puede cerrar) | `rf15-probado` | [RF15_apertura_caja.md](RF15_apertura_caja.md) |
| RF16 | Desembolso y caja (condiciones del desembolso) | Probado contra el anteproyecto, completo el 11/10/2026 (checklist, documentos vigentes, caja del día, saldo suficiente con entrega del gerente) | `rf16-probado` | [RF16_condiciones_desembolso.md](RF16_condiciones_desembolso.md) |
| RF17 | Desembolso y caja (comprobante antes de entregar) | Probado contra el anteproyecto, completo el 11/10/2026 (comprobante DES-2026-000001 impreso antes de confirmar, reimpresión marcada) | `rf17-probado` | [RF17_comprobante_desembolso.md](RF17_comprobante_desembolso.md) |
| RF18 | Desembolso y caja (movimientos, saldo teórico, reimpresión) | Probado contra el anteproyecto, completo el 11/10/2026 (movimientos y saldo teórico cuadrando exacto en 0 con CC-2026-00001) | `rf18-probado` | [RF18_movimientos_caja.md](RF18_movimientos_caja.md) |
| RF19 | Desembolso y caja (cierre con arqueo) | Probado contra el anteproyecto, completo el 11/10/2026 (motivo mínimo de 10 caracteres rechazado en el servidor, acta de arqueo con firmas) | `rf19-probado` | [RF19_cierre_arqueo.md](RF19_cierre_arqueo.md) |
| RF20 | Cobranza y mora (buscar préstamo, lista de trabajo) | Probado contra el anteproyecto, completo el 12/10/2026 (sin duplicado en mora, mensaje claro para un préstamo anulado) — falta solo la prueba con fechas reales en las 3 tarjetas, diferida a propósito (ver el documento, sección 9) | `rf20-probado` | [RF20_lista_trabajo_cobranza.md](RF20_lista_trabajo_cobranza.md) |
| RF21 | Cobranza y mora (deuda exigible, mora, IVA) | **Probado en pantalla el 13/10/2026** (tag `rf21-probado`) — `CC-2026-00027`, deuda exigible 588.513 Gs., coincide peso por peso con el motor y con Seguimiento de mora | `rf21-probado` | [RF21_deuda_exigible.md](RF21_deuda_exigible.md) |
| RF22 | Cobranza y mora (cobro en efectivo, vuelto) | Probado en pantalla el 12/10/2026 — cartel de confirmación OK (nombra la acción, "Sin vuelto (0 Gs.)"). "Cobrar" con una cuota realmente vencida queda para la prueba con fechas reales | `rf22-probado` | [RF22_cobro_efectivo.md](RF22_cobro_efectivo.md) |
| RF23 | Cobranza y mora (recibo y factura con IVA) | Probado en pantalla el 12/10/2026 — recibo y factura OK (monto único, "Firma de la cajera"/"N.º", domicilio sin referencia coloquial, tabla corregida). **Factura pendiente de revisión del contador** (DNIT) | `rf23-probado` | [RF23_recibo_factura.md](RF23_recibo_factura.md) |
| RF24 | Cobranza y mora (gestiones, promesas de pago) | Probado en pantalla el 12/10/2026 — gestión con resultado, fecha de promesa y monto OK; advertencia del servidor ("!") por monto faltante OK. **El control de pantalla (frenar antes de enviar) queda pendiente de confirmar, ver documento §10** | `rf24-probado` | [RF24_seguimiento_mora.md](RF24_seguimiento_mora.md) |
| RF25 | Cobranza y mora (reversión del cobro) | Probado en pantalla el 12/10/2026 — reversión rechazada y re-solicitada, autorización, panel, caja (Compensación con su N.º REV- y botón Reimprimir), recibo anulado (franja + sello diagonal), constancia de reversión y factura, todo OK | `rf25-probado` | [RF25_reversion_cobro.md](RF25_reversion_cobro.md) |
| RF26 | Cancelación (monto para cancelar) | Probado en pantalla el 12/10/2026 — CC-2026-00026, "Cancelar todo" dio exactamente el monto calculado de antemano (847.639 Gs.: vencido con mora 539.897 + capital no vencido 305.994 + interés devengado 1.748) | `rf26-probado` | [RF26_cancelacion_anticipada.md](RF26_cancelacion_anticipada.md) |
| RF27 | Cancelación (adelantar capital) | Probado en pantalla el 12/10/2026 — OK, incluidos los 2 ajustes de texto de la reprueba (vuelto en pantalla, formato del cartel) | `rf27-probado` | [RF27_adelantar_capital.md](RF27_adelantar_capital.md) |
| RF28 | Cancelación (constancia + finalizado) | Probado en pantalla el 12/10/2026 — CC-2026-00026 FINALIZADO, constancia CAN-2026-000004, factura 001-001-0000014 correcta (solo intereses/mora con IVA, sin capital) | `rf28-probado` | [RF28_constancia_cancelacion.md](RF28_constancia_cancelacion.md) |
| RF29 | Cancelación (cierre documental) | Probado en pantalla el 12/10/2026 — constancia DEV-2026-000002, escaneo cargado, cierre documental COMPLETO | `rf29-probado` | [RF29_cierre_documental.md](RF29_cierre_documental.md) |
| RF30 | Informes (panel con indicadores) | **Probado en pantalla el 13/10/2026** (tag `rf30-probado`) — cumple las 7 tarjetas, visibilidad real por perfil | — | [RF30_panel.md](RF30_panel.md) |
| RF31 | Informes (resumen financiero, mensual, cartera, proyección) | **Probado en pantalla el 13/10/2026** (tag `rf31-probado`) — totales cuadran, acceso del oficial bloqueado de verdad (whitelist); corregida la etiqueta de morosidad por capital vencido | — | [RF31_informes_dueno.md](RF31_informes_dueno.md) |
| RF32 | Informes (operación y cobranza) | **Probado en pantalla el 13/10/2026** (tag `rf32-probado`) — cumple los 9 informes; un cobro revertido nunca se suma al total; filtro "Cliente u operación" implementado en 6 de 7 (Caja afuera por diseño, ver §10) | — | [RF32_informes_operacion.md](RF32_informes_operacion.md) |
| RF33 | Informes (facturas del mes e IVA) | **Probado en pantalla el 13/10/2026** (tag `rf33-probado`) — numeración sin saltos, facturas anuladas marcadas y fuera del total, PDF coincide con pantalla | — | [RF33_facturas_iva.md](RF33_facturas_iva.md) |
| RF34 | Informes (estado de cuenta, reimprimir) | **Probado en pantalla el 13/10/2026** (tag `rf34-probado`) — ruta separada de /cobranza para que el oficial reimprima sin permiso de cobro; corregida la FALLA-1 de la auditoría de impresión (window.print() → Jasper, Informe PDF + Excel/CSV) | — | [RF34_estado_cuenta.md](RF34_estado_cuenta.md) |
| RF35 | Informes (filtrar por fecha, exportar PDF/Excel) | **Probado en pantalla el 13/10/2026** (tag `rf35-probado`) — cumple; corregidas las 2 exportaciones CSV que no auditaban (resumen mensual/financiero); PA-28/PA-29 anotadas como mejoras de bajo riesgo | — | [RF35_filtros_exportacion.md](RF35_filtros_exportacion.md) |
| RF36 | Seguridad (ingreso, cambio obligatorio de contraseña) | Probado contra el anteproyecto el 09 y el 10/10/2026 | `login-consolidado` | [procesos/login.md](procesos/login.md) |
| RF37 | Seguridad (administrador: crear/bloquear/inactivar/reactivar/restablecer) | Probado contra el anteproyecto, completo el 09-10/10/2026 (3 correcciones aplicadas y probadas) | `rf37-probado` | [RF37_usuarios.md](RF37_usuarios.md) |
| RF38 | Seguridad (opciones según el perfil) | **Probado en pantalla el 13/10/2026** (tag `rf38-probado`) — RNF11 cumple; D1 (menú sin candados) y D2 (ficha de cliente sin secciones sensibles en consulta) confirmados en pantalla | — | [RF38_perfiles.md](RF38_perfiles.md) |
| RF39 | Seguridad (parámetros + historial de cambios) | **Probado en pantalla el 13/10/2026, completo** (tag `rf39-probado`) — motivo obligatorio en el historial; TEA 0, montos negativos y edades cruzadas rechazados; tope legal con una sola fuente (`limites_tasa_usura`); feriados y límites de tasa con antes→después en la auditoría; gerente en consulta sin poder modificar; bloqueo de negocio mostrado como advertencia (no error); detalle de auditoría de `CAMBIAR_PARAMETRO` con motivo | `rf39-probado` | [RF39_parametros.md](RF39_parametros.md) |
| RF40 | Seguridad (auditoría con filtros) | **Probado en pantalla el 13/10/2026** (tag `rf40-probado`) — cobertura completa (ninguna operación de negocio sin auditar); RNF12 confirmado (cero `DELETE`, respaldado por trigger); filtros de Usuario y Entidad con lista desplegable (F1/F2); PDF/CSV con las 3 columnas nuevas (F3); Detalle sin cortar (F4); oficial/cajera con "Acceso restringido" | `rf40-probado` | [RF40_auditoria.md](RF40_auditoria.md) |

## Requisitos no funcionales (RNF01-18)

Los RNF no tienen fila propia en la tabla de arriba porque ninguno es un módulo con pantalla
propia — cada uno vive dentro del proceso que lo implementa. **Revisados contra el anteproyecto
el 13/10/2026**, ver [RNF_revision.md](RNF_revision.md) (tabla completa, texto exacto de cada
RNF, cómo se cumple, estado y qué falta). Los de seguridad del login (RNF08 a RNF12) siguen con
su detalle propio en [procesos/login.md](procesos/login.md) (ya probados, `login-consolidado`/
`rnf09-bloqueo-probado`).

**Probados el 13/10/2026 (Joel, en su laptop)**: RNF04 (Brave, Chrome, Firefox y Edge), RNF05
(sin internet — panel, informes, estado de cuenta, recibo y factura en PDF con logo) y RNF14
(rendimiento, medido con F12 → Red: ingreso 0,78 s, panel 0,74-1,00 s, clientes 0,30-0,50 s,
dentro de los umbrales de Doc. 00 §14.2). **RNF03 confirmado** contra el servidor real de la
institución: Windows Server, PostgreSQL 17 y pgAdmin 17 (el anteproyecto permite "Windows o
Linux" — Doc. 00 §14.6 asumía un servidor Ubuntu que no es el real, ya corregido en
`RNF_revision.md`). RNF17 ya probado (`rf39-probado`). Pendientes: RNF02 (prioridad baja),
RNF15/RNF16 (disponibilidad y respaldo diario en Windows Server — análisis del 13/10/2026, sin
implementar todavía).

## Procesos (cruzan varios RF)

Mismos dos estados que la tabla de arriba.

| Proceso | RF relacionados | Estado | Tag | Documento |
|---|---|---|---|---|
| Login, continuidad del administrador y recuperación de acceso | RF36, RF37, RNF08-RNF12 | Probado contra el anteproyecto | `login-consolidado` | [procesos/login.md](procesos/login.md) |
| Autorización firmada y consulta al buró | RF03, RF04 | Probado contra el anteproyecto, completo el 10/10/2026 | `rf04-probado` | [RF04_buro.md](RF04_buro.md) |
| Caja y desembolso | RF15-RF19 | Implementado, sin recorrido contra el anteproyecto | `d145-caja-simple` | Sin documento |
| Cobranza, mora y cancelación | RF20-RF29 | Implementado, sin recorrido contra el anteproyecto | `d145-caja-simple` | Sin documento |
| Auditoría (motor y reporte) | RF40, RNF12 | **Probado en pantalla el 13/10/2026** (tag `rf40-probado`) — ver [RF40_auditoria.md](RF40_auditoria.md); `procesos/login.md` §10 sigue teniendo el detalle de seguridad/login (RNF08-12) | `rf40-probado` | [RF40_auditoria.md](RF40_auditoria.md) |
| Informes y reportes | RF30-RF35 | **Probados en pantalla el 13/10/2026, los 6** (tags `rf30` a `rf35-probado`) — ver cada RF | — | Ver RF30-RF35 |
| Entrega (datos de prueba, respaldo, manual corto, copia) | — (E10, no es un RF) | En curso | — | Sin documento |

Procesos sin documento todavía: se crean la primera vez que se los vuelva a tocar, siguiendo la
regla de arriba.

## Pendientes

- ~~Crear con la estructura completa `RF01_cotizador.md`, `RF02_clientes.md` y
  `RF03_expediente.md`~~ — **hecho** el 11/10/2026, reconstruidos desde el historial de git.
  RF02 queda con su propio pendiente de reprueba formal (ver la tabla de arriba y
  [RF02_clientes.md](RF02_clientes.md) §10).
- **Motivos de rechazo EDAD/ZONA/POLITICA_LABORAL**: aprobado e implementado (desactivados en
  `sgcpc_db_pruebas`, `bd/03_parametros.sql` actualizado) — ver
  [RF10_rechazar.md](RF10_rechazar.md) §9/§12. Queda pendiente solo aplicar la misma migración
  a `sgcpc_db` (la base real) antes de entregar.
- **RF32 (informes)**: filtro real "Cliente u operación"/"C.I." que viaje al servidor — marcada
  "OBLIGATORIA" por Joel el 07/10/2026 — **implementado el 13/10/2026 en 6 de 7 informes**
  (Aprobaciones, Cobros, Desembolsos, Solicitudes, Gestiones, Consultas al buró; Caja queda afuera
  por diseño, ver [RF32_informes_operacion.md](RF32_informes_operacion.md) §6g/§10) y **probado
  en pantalla el 13/10/2026 (tag `rf32-probado`)**. Menor: ancho de columna de
  `informe_morosidad.jrxml` (D9c) y nombres de columna/orden de filas sin unificar entre reportes.
- **RF38 (seguridad por perfil)**: **corregido y probado en pantalla el 13/10/2026 (D2, tag
  `rf38-probado`)** — la cajera ya no ve los datos sensibles del cliente (ingresos, gastos,
  deudas, referencias) ni "Califica: puede continuar con el registro"; esas secciones no se
  dibujan en el servidor para quien solo tiene consulta. Ver el detalle completo (con
  archivo/línea) y 6 hallazgos más (ninguno es brecha de seguridad real) en
  [RF38_perfiles.md](RF38_perfiles.md) §6/§7/§10.
- **RF39 (parámetros) — probado en pantalla el 13/10/2026 (tag `rf39-probado`)**: ver
  [RF39_parametros.md](RF39_parametros.md) §9/§10/§11 para el detalle completo. Resuelto en esta
  sesión: observación del historial (ahora obligatoria), nombre de `credito.cuotas_alerta_garante`
  ("Cuotas vencidas para el vencimiento anticipado"), formato de `caja.monto_maximo_arqueo`, TEA 0
  y montos negativos (ahora rechazados), tope legal de TEA con una sola fuente
  (`limites_tasa_usura`, `credito.tea_tope_legal` retirado). Pendientes nuevos, sin implementar
  (§10): `credito.redondeo_efectivo` sin formato de Gs.; las 3 claves de "Representante legal"
  (investigadas, las 3 se usan, pero la combinada podría derivarse de las otras 2), ambos sin
  implementar todavía. Las 2 correcciones chicas encontradas en la misma prueba (aviso de D2
  como advertencia, no error; detalle de auditoría de `CAMBIAR_PARAMETRO` con motivo) también se
  probaron en pantalla y quedaron aprobadas (2a/2b).
- **RF40 (auditoría) — probado en pantalla el 13/10/2026 (tag `rf40-probado`)**, ver
  [RF40_auditoria.md](RF40_auditoria.md) §9/§10: cobertura completa (ninguna operación de
  negocio sin auditar) y RNF12 confirmado (cero `DELETE` en el código, **y respaldado por
  trigger** en `bd/02_indices.sql:108-147`). Resuelto en esta sesión: filtros de Usuario/Entidad
  con lista desplegable (F1/F2), PDF/CSV con Usuario escrito/IP/Navegador (F3), columna
  "Detalle" sin cortar (F4) — los 4 probados en pantalla. Confirmado que ya estaba resuelto (no
  es un pendiente real): quién modifica datos de un cliente y quién reemplaza cada documento.
  Pendientes reales, sin implementar: `RECALCULO_MORA_DIARIO` como "Sistema"; IP/navegador
  vacíos fuera del login (PA-28, propuesta con `ThreadLocal` en 3 archivos, documentada);
  permiso `CONSULTAR_AUDITORIA` fantasma (sin cablear, decisión de Joel); 3 detalles de
  maquetación del PDF (columna "Entidad" corta la palabra, sin logo, "Página 1 de    1") para el
  Lote 2.
