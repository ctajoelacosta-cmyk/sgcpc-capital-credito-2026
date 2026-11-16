# Anexo — Qué pasa por detrás (Escenas 00/05, tramo 4, rol cajera)

Mismo formato que [04_que_pasa_por_detras.md](04_que_pasa_por_detras.md): complementa
[00_apertura.md](00_apertura.md) y [05_desembolso.md](05_desembolso.md) con lo que pasa en el
servidor y en la base, verificado leyendo el código real (solo lectura, sin cambiar nada). Cubre
los pasos de la cajera recorridos hoy: login → panel → abrir caja → (cierre de la sesión del día
anterior y apertura de hoy) → bandeja de desembolso → ficha del desembolso → imprimir comprobante
→ confirmar desembolso → revisar la caja. Lo que no pude confirmar con una lectura directa, o que
quedó explícitamente para otro tramo, queda marcado **"a confirmar"**.

## 1. Login de la cajera (RF36)

- **Pantalla/URL**: `POST /acceso`.
- **Servlet/servicio**: `ServicioAutenticacion.autenticar` (mismo método para los 4 perfiles).
- **Lee**: `usuarios` (`UsuarioDAO.buscarPorNombreUsuario`), `parametros_sistema`
  (`seguridad.intentos_login`, `seguridad.minutos_bloqueo`).
- **Escribe**: condicional — si la cuenta estaba `BLOQUEADA` y ya pasó el tiempo de bloqueo,
  `desbloquearPorTiempoSiCorresponde` la reactiva antes de intentar el acceso; con credenciales
  correctas, `UsuarioDAO.registrarAccesoExitoso` (último acceso).
- **Cambios de estado**: ninguno propio de un login exitoso (la cuenta ya estaba `ACTIVA`).
- **Reglas/parámetros**: límite de intentos fallidos y minutos de bloqueo, ambos parametrizados.
- **Numeración/Archivos**: ninguno.
- **Auditoría**: `ACCESO_CONCEDIDO` ("Inicio de sesión"), entidad `USUARIO` — mismo patrón que el
  resto de los perfiles.

## 2. Panel de la cajera (RF30, RF38)

- **Pantalla/URL**: `GET /panel`.
- **Servlet/servicio**: `ControladorPanelServlet` → `ServicioPanel.calcularIndicadores(conexion,
  idUsuario, perfil = CAJERO)` — una sola función para todo el panel, arma distinto según el
  perfil, sin duplicar el método por rol (mismo criterio ya confirmado para oficial y gerente en
  [03_que_pasa_por_detras.md](03_que_pasa_por_detras.md) §1 y
  [04_que_pasa_por_detras.md](04_que_pasa_por_detras.md) §1).
- **Lee**: lo común a los 3 perfiles (`pagos`, `prestamos` en mora, `numeradores` +
  `parametros_sistema` para el aviso de timbrado); del bloque propio de la cajera —
  `sesiones_caja` (`ServicioCaja.obtenerSesionAbierta`, para "Sin caja abierta"/"Efectivo en
  caja"), `prestamos` (`PrestamoDAO.listarPendientesDesembolso`, TODOS los
  `PENDIENTE_DESEMBOLSO`, sin mirar el checklist) y, de nuevo,
  `ServicioFormalizacion.listarListosParaDesembolsar` (solo los que ya tienen el checklist
  completo — la MISMA lista que arma la bandeja de `/desembolso`, Sección 5).
- **Escribe**: condicional, mismo mecanismo de recálculo diario de mora ya documentado en
  `03_que_pasa_por_detras.md` §1 (idempotente, una sola vez por día, para cualquier usuario).
- **Cambios de estado**: ninguno propio de este paso.
- **Reglas/parámetros**: ninguno específico de este tramo.
- **Numeración/Archivos**: ninguno.
- **Auditoría**: ninguna propia de "ver el panel" (solo la condicional del recálculo de mora).
- **Nota**: "Pendiente de desembolso" (1) y "listo para desembolsar" (1) coinciden hoy porque el
  único préstamo `PENDIENTE_DESEMBOLSO` ya tiene el checklist completo desde el tramo 3 — ver
  B17 (ajustado) en `00_apertura.md`, sobre cuándo estos dos números pueden diferir.

## 3. Apertura de caja — pantalla /caja (RF15)

- **Pantalla/URL**: `GET /caja` (sin sesión abierta) y `POST /caja/abrir`.
- **Servlet/servicio**: `ControladorCajaServlet.mostrarFicha` / `procesarAbrir` →
  `ServicioCaja.calcularFondoSugerido` / `abrirCaja`.
- **Lee**: `sesiones_caja` (`obtenerAbierta`, para rechazar una segunda apertura); el fondo
  sugerido delega en `ServicioFormalizacion.calcularFondoSugeridoDesembolso`, que suma
  `prestamos.monto_aprobado` de los `PENDIENTE_DESEMBOLSO` con checklist completo (la misma
  lista de la Sección 2).
- **Escribe**: `INSERT sesiones_caja` (`saldo_inicial` = lo escrito en el campo,
  `estado_sesion = 'ABIERTA'`, `id_cajero`). **No inserta ningún `movimiento_caja` propio** — el
  fondo inicial vive solo como esa columna (de ahí la fila fija "Apertura (fondo inicial)" que
  agrega la JSP en la tabla de Movimientos, sin ser una fila real).
- **Cambios de estado**: ninguna sesión → `ABIERTA`.
- **Reglas/parámetros que controla**: índice único parcial de `sesiones_caja` — si ya hay una
  `ABIERTA` (de cualquier cajera), `CajaYaAbiertaException` rechaza antes de escribir nada ("Ya
  hay una caja abierta desde el [fecha hora] ([usuario]). No se abrió una nueva.").
- **Numeración/Archivos**: ninguno.
- **Auditoría**: `ABRIR_CAJA` ("Abrir caja"), entidad `SESION_CAJA`, con el saldo inicial en el
  detalle.
- **Mensaje de éxito exacto**: **"Caja abierta."**
- **Relación con la entrega de efectivo del gerente**: ver C9 en `00_apertura.md` — el fondo
  inicial YA representa la entrega física que el gerente le dio en mano a la cajera;
  `registrarEntregaGerente` es un mecanismo aparte, solo para refuerzos **durante** el día, con
  perfil gerente/administrador obligatorio (separación de funciones).

## 4. Nota — Cierre de la sesión del día anterior y apertura de hoy (RF19, RF15)

- **Qué pasó**: entre el Paso 1 y el Paso 2 de `05_desembolso.md`, Joel (como cajera) cerró la
  sesión N.º 1 (abierta 21/10/2026 22:04, fondo 3.000.000) y abrió la N.º 2 (22/10/2026 06:09,
  mismo fondo) — verificado con `SELECT` sobre `sesiones_caja` en `sgcpc_db_pruebas`: la sesión 1
  cerró con saldo teórico 3.000.000, saldo físico 3.000.000, diferencia **0**, sin motivo (no se
  exige cuando la diferencia es 0).
- **Pantalla/URL del cierre en sí**: `POST /caja/cerrar` → `ServicioCaja.cerrarCaja` — **a
  confirmar en detalle en el tramo 6** (pantalla, confirmación, mensaje de éxito exactos; hoy
  solo se verificó el resultado en la base, no el recorrido en pantalla).
- **¿El sistema avisa o impide operar con una caja de un día anterior?** Sí, verificado en el
  código: `ServicioCaja.esDeHoy(SesionCaja)` compara la fecha de `fecha_hora_apertura` con hoy;
  si la sesión `ABIERTA` no es de hoy, `validarSesionDeHoy` lanza `CajaDeOtroDiaException` con
  el mensaje exacto (`mensajeCajaDeOtroDia`): **"La caja del [fecha] sigue abierta: ciérrela y
  rinda antes de operar hoy."** — mismo texto en los 3 controladores que dependen de una caja de
  hoy (Caja, Cobranza, Desembolso). **No se disparó en pantalla hoy**: la cajera cerró la sesión
  1 antes de empezar a desembolsar, así que `ServicioDesembolso.asegurarCondicionesDesembolso`
  encontró directamente la sesión 2 (de hoy) ya abierta.
- **Auditoría**: `CERRAR_CAJA` ("Cerrar caja", sesión 1) y `ABRIR_CAJA` ("Abrir caja", sesión 2)
  — **a confirmar con `SELECT` sobre `auditoria_operaciones`** si hace falta el detalle exacto
  (no se verificó en esta nota, solo en `sesiones_caja`).

## 5. Bandeja de préstamos listos para desembolsar — /desembolso (RF16)

- **Pantalla/URL**: `GET /desembolso`.
- **Servlet/servicio**: `ControladorDesembolsoServlet.mostrarLista` →
  `ServicioFormalizacion.listarListosParaDesembolsar`.
- **Lee**: `PrestamoDAO.listarPendientesDesembolso` (`estado_prestamo = 'PENDIENTE_DESEMBOLSO'
  ORDER BY fecha_formalizacion`), filtrado por `esChecklistCompleto`/`obtenerChecklist` (RF13, el
  mismo método ya confirmado en el tramo 3 y en el panel, Sección 2); el buscador
  (`filtrarPrestamosPorTexto`) filtra en memoria sobre la lista ya filtrada.
- **Escribe**: nada.
- **Permiso**: `DESEMBOLSAR` (ruta `/desembolso`) — `TOTAL` solo para cajero (y administrador),
  `NINGUNO` para gerente y oficial.
- **Numeración/Archivos**: ninguno.
- **Auditoría**: ninguna por ver la bandeja.

## 6. Ficha del desembolso — /desembolso/{id}: resumen y condiciones (RF16, RF17, RF18)

- **Pantalla/URL**: `GET /desembolso/{idPrestamo}`.
- **Servlet/servicio**: `ControladorDesembolsoServlet.mostrarFicha`.
- **Lee**: `prestamos`, `personas` (cliente), `documentos_expediente` (checklist vía
  `obtenerChecklist`, y el `CONTRATO_GENERADO` vigente vía `obtenerContratoGeneradoVigente`, para
  su `fecha_documento`), `parametros_sistema` (`formalizacion.dias_validez_documentos`),
  `sesiones_caja` (`obtenerSesionAbierta`), `movimientos_caja` (`sumarMovimientos`, para el
  saldo teórico antes/después).
- **Escribe**: nada — ficha de solo lectura; genera un `identificadorConfirmacion` (UUID) nuevo
  en cada `GET`, para la idempotencia del `POST /confirmar` (Sección 8).
- **Reglas/parámetros que controla "Condiciones para desembolsar" (RF16)**: las 4 — checklist
  completo (RF13), documentos generados vigentes (`validarDocumentosVigentes`, pura, contra
  `fecha_documento + formalizacion.dias_validez_documentos`, hoy 5 días), caja abierta y de hoy
  (`esDeHoy`), saldo teórico ≥ monto aprobado. Las mismas 4 se revalidan, en el servidor, tanto al
  imprimir el comprobante (Sección 7) como al confirmar (Sección 8) —
  `ServicioDesembolso.asegurarCondicionesDesembolso`, un solo método para las 3 pantallas.
- **Deducciones**: no sale de ningún cálculo ni tabla — literal `BigDecimal.ZERO`, coherente con
  RN-08/RN-17 (`cargos_operacion` sin usar).
- **Numeración/Archivos**: ninguno nuevo en este paso.
- **Auditoría**: ninguna por ver la ficha.
- **C11 (clasificación pendiente)**: documentos vencidos con firmados ya cargados no tienen
  ningún camino de salida puntual dentro de Formalización/Desembolso — ver el detalle completo
  en `05_desembolso.md`.

## 7. Imprimir comprobante para firma — PDF comprobante_desembolso (RF17)

- **Pantalla/URL**: `GET /desembolso/{idPrestamo}/comprobante`, con el préstamo todavía
  `PENDIENTE_DESEMBOLSO`.
- **Servlet/servicio**: `ControladorDesembolsoServlet` (rama `/comprobante` del `doGet`) →
  `ServicioDesembolso.generarComprobantePreDesembolso`.
- **Lee**: `prestamos`, revalida las 4 condiciones de la Sección 6
  (`asegurarCondicionesDesembolso`) antes de generar nada; `solicitudes_credito`, `personas`,
  `parametros_sistema` (`empresa.*` vigentes a la fecha), `cuotas`/`planes_pago`
  (`listarCuotasVigentes`, para la cuota con IVA).
- **Escribe**: si es la primera vez, `numeradores` (clave `DESEMBOLSO`, `FOR UPDATE`) +
  `UPDATE prestamos.numero_comprobante_desembolso`; si ya tenía número, no escribe nada nuevo
  (reimpresión).
- **Cambios de estado**: ninguno (el préstamo sigue `PENDIENTE_DESEMBOLSO`).
- **Plantilla**: `reportes/comprobante_desembolso.jrxml` — sin ningún cálculo propio, dos bandas
  (`title` = ORIGINAL, `summary` = COPIA) con el mismo contenido.
- **¿Se guarda el PDF?** No — se genera al vuelo en cada pedido y se devuelve directo en la
  respuesta HTTP; nunca se escribe a disco ni se cachea una versión vieja. Solo persiste el
  número (`numero_comprobante_desembolso`) y el registro de auditoría.
- **Numeración**: `String.format("DES-%d-%06d", año, secuencia)` — correlativo por año,
  independiente del numerador `CC` de la operación (6 dígitos contra 5 — ver B21).
- **Auditoría**: `IMPRIMIR_COMPROBANTE_DESEMBOLSO` (primera vez) o
  `REIMPRIMIR_COMPROBANTE_DESEMBOLSO` (reimpresiones), entidad `PRESTAMO` — **ninguna de las dos
  tiene nombre legible en `EtiquetasAuditoria`** (ver B23: eran las únicas 2 operaciones de todo
  el sistema sin mapear). **Ya no es así desde el 25/10/2026** (commit `99a77b3`): las dos tienen
  su nombre en español y hoy hay 64 operaciones etiquetadas.

## 8. Confirmar desembolso — movimientos de caja y saldo (RF18)

- **Pantalla/URL**: `POST /desembolso/{idPrestamo}/confirmar`.
- **Servlet/servicio**: `ControladorDesembolsoServlet.procesarConfirmar` →
  `ServicioDesembolso.confirmarDesembolso`, una sola transacción.
- **Lee**: `prestamos` (revalida `PENDIENTE_DESEMBOLSO`), `desembolsos`
  (`obtenerPorIdentificadorConfirmacion`, para la idempotencia), reutiliza
  `asegurarCondicionesDesembolso` (Sección 6) para las 4 condiciones, exige además
  `numero_comprobante_desembolso != null` (el comprobante ya impreso, Sección 7) y
  `confirmoEntregaEfectivo = true` (la casilla, revalidada también en el servidor).
- **Escribe**: `INSERT desembolsos` (snapshot del monto, número de comprobante,
  `identificador_confirmacion`); `INSERT movimientos_caja` (tipo `DESEMBOLSO`, signo −1, mismo
  monto, `referencia_tipo = DESEMBOLSO`); `UPDATE prestamos` (`estado_prestamo = 'ACTIVO'`,
  `fecha_desembolso = hoy`) — condicionado a que siga `PENDIENTE_DESEMBOLSO` (carrera de dos
  pestañas, RF14).
- **Cambios de estado**: préstamo `PENDIENTE_DESEMBOLSO` → `ACTIVO`. `solicitudes_credito` **no
  cambia** — sigue `APROBADA` durante toda la formalización y el desembolso.
- **Reglas/parámetros**: las mismas 4 condiciones de RF16, más las 2 de RF17 (comprobante
  impreso, casilla confirmada).
- **¿La fecha de desembolso cambia el plan?** No las fechas ni montos de las cuotas (ya fijos al
  formalizar); sí es la referencia que usa `ServicioMotorFinanciero.inicioPeriodoTeorico` para
  el inicio del período de interés de la 1.ª cuota (relevante para la cobranza futura, no para
  hoy).
- **Numeración**: ninguna nueva — reutiliza el `DES-...` ya asignado en la Sección 7.
- **Auditoría**: `DESEMBOLSAR` ("Desembolsar préstamo"), entidad `PRESTAMO` — con nombre legible,
  a diferencia de las dos operaciones de la Sección 7.
- **Doble confirmación**: `identificador_confirmacion` (UUID del `GET`) se busca primero; si ya
  existe, no repite nada y comitea igual (cubre doble clic y volver atrás con el mismo
  formulario); la restricción `UNIQUE` de la columna es la red final ante una carrera real
  (`SQLState 23505`, tratado también como éxito).
- **Mensaje de éxito exacto**: **"Desembolso de [operación] confirmado ([monto])."**, con un
  botón "Imprimir comprobante" (la misma ruta de la Sección 7, ya tratada como reimpresión).

## 9. Revisar la caja después del desembolso — /caja (RF18)

- **Pantalla/URL**: `GET /caja`, con la sesión N.º 2 todavía abierta.
- **Servlet/servicio**: `ControladorCajaServlet.mostrarFicha` → `ServicioCaja.
  obtenerSesionAbierta` + `calcularSaldoTeorico` + `listarMovimientosConComprobante`.
- **Lee**: `sesiones_caja`, `movimientos_caja` (`listarMovimientos`), y por cada movimiento con
  `referencia_tipo = 'DESEMBOLSO'`, la fila real de `desembolsos` (`DesembolsoDAO.obtenerPorId`)
  para completar número de comprobante y la URL de "Reimprimir".
- **Escribe**: nada — pantalla de solo lectura (no se tocó "Cierre y rendición" hoy).
- **Reglas/parámetros**: `calcularSaldoTeorico` = `saldo_inicial + SUM(movimientos.monto ×
  signo)` — misma fórmula que ya usan C10 y la Sección 8 (0 Gs. hoy: 3.000.000 − 3.000.000).
- **Permiso del botón "Reimprimir"**: `VER_COMPROBANTES_COBRANZA` ≠ `NINGUNO` — la cajera tiene
  `CONSULTA` (lo ve); el oficial tiene `NINGUNO`. La ruta real (`/desembolso/{id}/comprobante`)
  ya está protegida aparte por `FiltroAutorizacion` (defensa en profundidad).
- **Numeración/Archivos**: ninguno nuevo.
- **Auditoría**: ninguna por ver la ficha (la reimpresión, si se usara el botón, auditaría igual
  que la Sección 7).

## Matriz de permisos relevante para este tramo

| Permiso | Ruta/acción | Gerente | Oficial | Cajero |
|---|---|---|---|---|
| `ABRIR_CERRAR_CAJA` | `/caja` (abrir, cerrar) | CONSULTA | NINGUNO | TOTAL |
| `ENTREGAR_RECIBIR_EFECTIVO_CAJA` | `/caja/entregar` | TOTAL | NINGUNO | TOTAL (bloqueado también en el servicio por perfil, ver C9) |
| `DESEMBOLSAR` | `/desembolso` (lista, ficha, comprobante, confirmar) | NINGUNO | NINGUNO | TOTAL |
| `AUTORIZAR_REVERSION_O_DIFERENCIA` | `/caja/revisar-diferencia` | TOTAL | NINGUNO | NINGUNO |
| `VER_COMPROBANTES_COBRANZA` | Botón "Reimprimir" en `/caja` (defensa en profundidad) | CONSULTA | NINGUNO | CONSULTA |
| `ADMINISTRAR_PARAMETROS` | `/parametros` (incluye `formalizacion.dias_validez_documentos`, ver C11) | CONSULTA | NINGUNO | NINGUNO |

## A confirmar

- El cierre de la sesión 1 (Sección 4) y el detalle de su auditoría — pantalla, confirmación y
  mensaje de éxito exactos — quedan para el tramo 6 (cierre del día); hoy solo se verificó el
  resultado en `sesiones_caja` con `SELECT`.
- Si el recálculo diario de mora (Sección 2) ya había corrido antes de que la cajera abriera su
  panel hoy (mismo punto "a confirmar" ya marcado en `03_que_pasa_por_detras.md` §1 y
  `04_que_pasa_por_detras.md`) — no se determinó sin consultar la base.
- El mensaje y la pantalla exactos de "Entrega de efectivo a la caja" (gerente) y de "Diferencias
  de arqueo sin revisar" — ninguno de los dos se recorrió hoy (ver "Completitud del tramo 4" en
  `05_desembolso.md`).
- Si existen, fuera de los 17 archivos revisados para B23, otras rutas que escriban directamente
  en `auditoria_operaciones` sin pasar por `ServicioAuditoria` (ej. algún script de carga de
  datos de prueba) — no se buscó fuera del código de la aplicación en ejecución.
