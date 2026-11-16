# RF15 — Apertura de caja con fondo inicial y bloqueo de la caja de un día anterior

**Estado: cerrado y probado en pantalla completo el 11/10/2026 — tag `rf15-probado`.** El texto
se cumple sin excepción: la cajera abre la caja del día con un fondo inicial, y una caja de un
día anterior bloquea entregar efectivo y desembolsar (revalidado en el servidor), quedando
solo la opción de cerrarla. Quedan 4 mejoras menores (sección 10, a-d, compartidas con RF16),
que NO se hacen en esta vuelta.

## 1. Qué exige el anteproyecto

`docs/RF_ANTEPROYECTO.md`, RF15 (módulo Desembolso y caja):

> El sistema debe permitir a la cajera abrir la caja del día con un fondo inicial, y no permitir
> operar si quedó abierta una caja de un día anterior.

## 2. Cómo funciona

**a) Dónde está y cómo se abre.** Pantalla `/caja` (`caja/ficha.jsp`): si no hay ninguna sesión
`ABIERTA`, muestra un formulario con un solo campo — "Fondo inicial" (precargado con el "fondo
sugerido": la suma de los montos aprobados de los préstamos `PENDIENTE_DESEMBOLSO` con checklist
ya completo, `ServicioCaja.calcularFondoSugerido`, editable) — y el botón "Abrir caja". Postea a
`POST /caja/abrir` → `ServicioCaja.abrirCaja(idUsuarioCajero, saldoInicial)`.

**Quién**: la ruta `/caja` está protegida por `Permiso.ABRIR_CERRAR_CAJA`
(`FiltroAutorizacion.java:61`) — **TOTAL solo para la cajera**; el gerente tiene **CONSULTA**
(puede ver `/caja` por `GET`, pero cualquier `POST` —abrir, cerrar— exige TOTAL y le da 403,
`ServicioAutorizacion.java:45`); el oficial, **NINGUNO**. El administrador, TOTAL siempre. Esto
coincide exacto con la matriz de permisos ya existente desde el diseño de E6.

**Fondo inicial — mínimo, máximo, ¿puede ser 0?**: la única validación es
`saldoInicial == null || saldoInicial.compareTo(BigDecimal.ZERO) < 0` → rechaza con "Ingrese el
saldo inicial de la caja." — **0 es válido** (no hay mínimo distinto de 0), y **no hay máximo**
en ningún lado (ni parámetro, ni validación, ni restricción de columna —
`sesiones_caja.saldo_inicial NUMERIC(18,0) NOT NULL`, sin `CHECK`).

**¿De dónde sale ese efectivo, queda registrado quién lo entregó?** El comentario del código es
explícito sobre el supuesto: *"la cajera abre con el fondo que el gerente ya le entregó en
mano"* (`ServicioCaja.java:109`) — pero esa entrega física INICIAL **no tiene su propio
registro**: no hay un movimiento `ENTREGA_GERENTE` asociado a la apertura, ni un campo que diga
quién entregó el fondo inicial. Lo único que queda es la auditoría `ABRIR_CAJA` (actor =
la cajera que abrió, con el monto que escribió) — se confía en que ese número es correcto, sin
un segundo registro independiente del lado del gerente. (Las entregas de efectivo QUE SUCEDEN
DESPUÉS, durante el día, sí quedan completamente registradas con quién las hizo — ver RF16 §6f
y la sección 2 de este documento, "Entregar efectivo".)

**¿Una caja por cajera o una sola para todos?** **Una sola, compartida por todo el sistema** —
el comentario de la clase lo dice literalmente: *"Una sola caja ABIERTA en todo el sistema"*
(`ServicioCaja.java:55-58`), reforzado por el índice único parcial `ux_sesiones_caja_una_abierta`
(sección 6d). Si hay varias cajeras, solo una puede tener la caja abierta a la vez — no existe
el concepto de "mi caja" por persona.

## 3. Auditoría

| Evento | Entidad | Cuándo |
|---|---|---|
| `ABRIR_CAJA` | SESION_CAJA | Se abrió la caja — "Abrió la caja con saldo inicial X Gs." |
| `CERRAR_CAJA` | SESION_CAJA | Se cerró (ver RF15 §2, cierre) — con teórico/físico/diferencia |
| `ENTREGAR_EFECTIVO_CAJA` | MOVIMIENTO_CAJA | El gerente entregó efectivo durante el día (no es la apertura) |
| `REVISAR_DIFERENCIA_CAJA` | SESION_CAJA | El gerente revisó una diferencia de cierre, con su observación |

Un intento de `POST /caja/abrir` sin permiso (ej. el oficial, o el gerente) queda auditado como
acceso denegado (`FiltroAutorizacion.auditarAccesoDenegado`, mecanismo genérico del sistema, no
específico de caja).

## 4. Dónde está

- `servicios/ServicioCaja.java` — `abrirCaja`, `obtenerSesionAbierta`, `esDeHoy`,
  `calcularFondoSugerido`, `calcularSaldoTeorico`, `cerrarCaja`.
- `dao/SesionCajaDAO.java` (`obtenerAbierta`, `insertar`, `marcarCerrada`).
- `controladores/ControladorCajaServlet.java` (`GET /caja`, `POST /caja/abrir`).
- `webapp/WEB-INF/vistas/caja/ficha.jsp`.
- `infraestructura/Reloj.java` (zona horaria, sección 6c).
- Tabla `sesiones_caja`, índice único parcial `ux_sesiones_caja_una_abierta`
  (`bd/02_indices.sql:17-19`).

## 5. Dependencias

- **Depende de**: nada de otro RF — es el punto de entrada de todo el flujo de caja/desembolso
  del día.
- **Lo afecta un cambio en**: nada compartido relevante (la lógica de apertura es autocontenida).
- **A quién afecta un cambio acá**: RF16 (desembolso exige caja abierta Y del día — este mismo
  documento, sección 2/6); E7 (cobros, misma exigencia de caja abierta); RF17 (comprobante de
  desembolso, dentro de la misma sesión).

## 6. Análisis

### 11/10/2026 — revisión completa de RF15 contra el texto firmado (pedido de Joel, solo lectura)

**Método**: lectura completa de `ServicioCaja.java`, `ControladorCajaServlet.java`,
`caja/ficha.jsp`, `FiltroAutorizacion.java`/`ServicioAutorizacion.construirMatriz`,
`bd/01_esquema.sql` (tabla `sesiones_caja`), `bd/02_indices.sql` (índice único parcial), y
`infraestructura/Reloj.java`.

### b) "No permitir operar si quedó abierta una caja de un día anterior"

**Qué operaciones se bloquean**: todo lo que depende de que la sesión esté "de hoy"
(`ServicioCaja.esDeHoy`), revalidado SIEMPRE en el servidor, dentro de la transacción de cada
operación, no solo en la pantalla:
- `ServicioCaja.registrarEntregaGerente` (línea 165-167): rechaza con el mismo mensaje si la
  sesión abierta no es de hoy.
- `ServicioDesembolso.confirmarDesembolso` (ver RF16 §6f): mismo chequeo, mismo mensaje.
- **Abrir una caja nueva**: no aplica un chequeo de "es de hoy" porque `abrirCaja` rechaza
  directamente si YA hay cualquier sesión abierta (de hoy o de otro día) — no se puede abrir una
  segunda mientras la primera, sea del día que sea, siga abierta. El único camino con una caja
  vieja es cerrarla.
- **Cerrar la caja**: es la ÚNICA acción que SÍ se permite sobre una caja de otro día —
  `ServicioCaja.cerrarCaja` no tiene ningún chequeo de `esDeHoy` (se puede cerrar y rendir una
  caja de ayer sin restricción, es justamente la salida de ese estado).

**Qué ve la cajera**: en `/caja`, un cartel rojo (`alert-danger`) arriba de todo: *"La caja del
dd/mm/aaaa sigue abierta: ciérrela y rinda antes de operar hoy."* (`caja/ficha.jsp:73-78`) — y
la card "Entregar efectivo a la caja" (que solo la ve el gerente) queda oculta
(`sesionEsDeHoyFicha && esGerenteCaja`), mientras que "Cerrar caja" sigue disponible siempre
para quien no es gerente (`!esGerenteCaja`, sin condición de fecha).

**Cómo se resuelve**: cerrándola con su arqueo normal (`/caja/cerrar`, mismo formulario y mismo
flujo que cualquier cierre — saldo físico contado, motivo si hay diferencia). Quién puede: la
misma matriz de siempre — solo la cajera (o el administrador); el gerente no puede cerrar
(`CONSULTA`, no `TOTAL`).

**¿El control está en el servidor para cada operación o solo en la pantalla?** En el servidor,
siempre — confirmado en los 2 puntos que de verdad importan (entregar efectivo, desembolsar):
los dos vuelven a llamar `esDeHoy` dentro de su propia transacción, sin confiar en lo que la
pantalla mostraba. La pantalla solo anticipa visualmente lo mismo que el servidor ya garantiza.

### c) Fecha del "día"

**¿Hora del servidor?** No directamente — `Reloj.hoy()`/`Reloj.ahora()` usan explícitamente
`ZoneId.of("America/Asuncion")` (`Reloj.java:24`), fijada en el código, **independiente de la
zona horaria del sistema operativo** del servidor (y el lado PostgreSQL se fija aparte con
`ALTER DATABASE`, para que las dos fuentes de "ahora" coincidan). Esto es una decisión de diseño
ya documentada (D-110), no algo que dependa de cómo esté configurado el reloj de Windows/Linux
del servidor.

**¿Qué pasa con una caja abierta cerca de la medianoche?** `esDeHoy` compara
`fecha_hora_apertura.toLocalDate()` contra `Reloj.hoy()` con igualdad exacta de fecha calendario
— sin ningún margen de gracia. Una caja abierta a las 23:58 del día X deja de ser "de hoy" en el
instante exacto en que el reloj (de la zona America/Asunción) marca las 00:00 del día X+1 —
aunque hayan pasado literalmente 2 minutos. No es un error: es el comportamiento esperado de
comparar por fecha calendario, consistente con que "el día" de una caja es, por definición, el
turno de un día de atención, no una ventana de 24 horas desde que se abrió.

### d) Doble apertura y concurrencia

**Dos clics / F5 en el mismo formulario**: `abrirCaja` primero lee si ya hay una sesión abierta
(`sesionCajaDAO.obtenerAbierta`) — si el primer clic ya la abrió, el segundo la encuentra y
rechaza con un mensaje explícito: *"Ya hay una caja abierta desde el dd/mm/aaaa hh:mm (usuario).
No se abrió una nueva."* (corrección de Joel tras un incidente de la prueba manual de E6 — antes
el rechazo era silencioso). **Consolidación RF08-RF19 (11/10/2026, A.8)**: este mensaje ahora es
`CajaYaAbiertaException` (subtipo de `NegocioException`) — se muestra como advertencia (ícono y
"Entendido"), no como error, mismo criterio que RF11 "fuera de plazo" y RF19 "caja ya cerrada".

**Dos pestañas / dos cajeras intentando abrir casi al mismo tiempo (carrera real)**: el chequeo
de arriba (`SELECT` simple, sin `FOR UPDATE`) **no cierra la ventana por sí solo** — si dos
transacciones leen "no hay sesión abierta" casi al mismo instante, las dos podrían llegar al
`INSERT`. El respaldo final es el **índice único parcial `ux_sesiones_caja_una_abierta`**
(`ON sesiones_caja (estado_sesion) WHERE estado_sesion = 'ABIERTA'`, `bd/02_indices.sql:17-19`):
solo puede existir UNA fila con `estado_sesion = 'ABIERTA'` en toda la tabla. La segunda
transacción que intente insertar una segunda fila `ABIERTA` choca con la base, no con el chequeo
previo — en ese caso específico (la carrera real, no el caso normal con el chequeo) el mensaje
que recibe la segunda cajera es el genérico *"No se pudo completar la operación. Intente
nuevamente."* (capturado como `SQLException` en `ControladorCajaServlet.procesarAbrir`), no el
mensaje específico de "ya hay una caja abierta desde...". **Financieramente da igual** (nunca
quedan dos cajas `ABIERTA` a la vez, la base lo impide de raíz), pero el mensaje en ese caso
puntual es menos informativo que el del chequeo normal — mismo patrón que ya se documentó para
el número de operación único (RF12) y la carrera de dos pestañas de aprobar/rechazar (RF09/RF10).

**CSRF**: cubierto por el mecanismo global (`FiltroCsrf`), sin nada específico de esta pantalla.

**Auditoría**: ver sección 3 — `ABRIR_CAJA` queda siempre, con el monto declarado.

### e) Propuesta de prueba para "caja de un día anterior" (SOLO análisis, sin ejecutar)

Hoy no existe ninguna caja de un día anterior en los datos de prueba (todas las sesiones
cerradas lo están correctamente, y la única forma de llegar a ese estado es dejar una abierta y
esperar a que cambie el día real). Para poder repetir la prueba sin esperar un día de calendario
completo, propongo un script SQL — **creado en el repositorio, NO ejecutado todavía**:

`pruebas/rf15_caja_dia_anterior.sql` (nuevo archivo, ver su contenido completo en el repo) —
inserta una sesión `ABIERTA` con `fecha_hora_apertura` de ayer a las 08:00, con el usuario
`cajera`, un fondo de 500.000 Gs. (valor usado en la prueba real, sección 9 — el script original
proponía 1.000.000, ajustado antes de correrlo). El script:
1. **Se niega a correr** si ya hay una sesión `ABIERTA` (lo revisa con un bloque `DO` que lanza
   una excepción clara antes de intentar nada) — para no pisar una prueba en curso.
2. **Alcance**: pensado para `sgcpc_db_pruebas` únicamente — el encabezado del archivo incluye
   el comando `psql` exacto apuntando a esa base, con la misma advertencia que ya usan las
   migraciones de datos del proyecto.
3. **Para deshacerla**: no hace falta ningún `DELETE` — se cierra como cualquier caja real,
   desde `/caja`, con "Cerrar caja" (deja su propio registro de auditoría coherente).

**Ejecutado** el 11/10/2026, con la aprobación explícita de Joel, SOLO contra `sgcpc_db_pruebas`
— confirmado antes (sin lectura, no había ninguna sesión `ABIERTA`) y después (una sesión
`ABIERTA`, cajera Sofía Martínez, apertura 10/10/2026 08:00, fondo 500.000) con una lectura.
Resultado completo en la sección 9.

## 7. Decisiones

No se encontraron hallazgos que exijan programar algo — RF15 cumple el texto firmado tal cual
ya estaba implementado. Confirmado en pantalla el 11/10/2026 (sección 9). Quedan 4 mejoras
menores (sección 10, a-d, compartidas con RF16), anotadas para una próxima vuelta.

## 8. Historial de cambios

| Fecha | Qué cambió | Commit | Tag |
|---|---|---|---|
| 11/10/2026 | Consolidación RF08-RF19 (Lote 1): "Ya hay una caja abierta..." y "la caja de otro día sigue abierta..." ahora son advertencia, no error (A.8); impresión del comprobante gateada por las 4 condiciones (A.5); saldo negativo en rojo (A.4) | *(pendiente)* | — |
| 11/10/2026 | Probado en pantalla completo con el script `pruebas/rf15_caja_dia_anterior.sql` (fondo 500.000), cajera Sofía Martínez | *(pendiente)* | `rf15-probado` |
| 11/10/2026 | Análisis completo contra el texto firmado, sin cambios de código; creado `pruebas/rf15_caja_dia_anterior.sql` | *(pendiente)* | — |

## 9. Pruebas realizadas en pantalla

**11/10/2026 (Joel), después del Clean and Build, cajera Sofía Martínez, con el script de la
sección 6e ejecutado contra `sgcpc_db_pruebas`:**
- **Caja de ayer** (sesión abierta el 10/10/2026 con fondo 500.000, vía el script): en `/caja`,
  el cartel rojo exacto *"La caja del 10/10/2026 sigue abierta: ciérrela y rinda antes de
  operar hoy."*, con solo la opción de cerrarla — confirma la sección 6b/6c.
- **Mismo bloqueo en desembolso**: en `/desembolso/{id}`, la condición de caja mostró *"La caja
  abierta no es de hoy: ciérrela y rinda antes de desembolsar."* — confirma que el chequeo
  `esDeHoy` se revalida también desde ese lado, con su propio texto.
- **Cierre de la caja de ayer**: con 500.000 contados (igual al fondo, sin diferencia) →
  "sin diferencia", cerrada correctamente — confirma que cerrar sigue disponible sin
  restricción de fecha (sección 6b).
- **Apertura normal después del cierre**: el fondo sugerido mostró 5.000.000 (el monto de
  CC-2026-00001, único préstamo `PENDIENTE_DESEMBOLSO` con checklist completo en ese momento) —
  confirma `calcularFondoSugerido`.

**Resultado: RF15 queda probado completo.** 4 mejoras menores encontradas junto con RF16
(sección 10, a-d), NO corregidas en esta vuelta.

**12/10/2026 (Joel), cajera, ciclo real completo (sin script)**: cerró la caja del 11/10/2026 a
las 08:12 (diferencia 0), abrió la caja de hoy a las 08:13 con fondo 0 — sin falla. El primer
cobro del día (adelanto de capital de Mirtha, ver `RF27_adelantar_capital.md` §9) entró en esa
caja nueva: movimiento `COBRO` 500.000, movimiento `VUELTO` −16.850, saldo teórico resultante
483.150 Gs.

## 10. Pendientes y trabajo futuro

Mejoras menores encontradas en la prueba de pantalla del 11/10/2026 (compartidas con RF16,
mismo pedido de Joel) — **NO corregidas en esta vuelta**:

a) ~~El botón "Imprimir comprobante para firma" (RF17) está disponible aunque no se cumplan las
   4 condiciones de RF16~~ — **corregido el 11/10/2026** (consolidación RF08-RF19, Lote 1, A.5) —
   ver `RF16_condiciones_desembolso.md` §10a. Pendiente de reprueba en pantalla.
b) ~~En la pantalla de Caja, "Saldo teórico después" muestra un número negativo~~ —
   **corregido el 11/10/2026** (A.4) — ver `RF16_condiciones_desembolso.md` §10b. Pendiente de
   reprueba en pantalla.
c) La entrega de efectivo del gerente queda con comprobante "-" en la lista de movimientos —
   evaluar un comprobante de entrega de efectivo con firma del gerente y la cajera (corresponde
   a RF18, no a RF15, pero se originó en esta prueba). Sin tocar en esta tanda.
d) ~~Confirmaciones con texto explícito: "Sí, abrir caja", "Sí, cerrar caja" y "Sí, entregar"~~ —
   las 3 ya estaban implementadas; "Sí, cerrar caja" confirmado en pantalla
   (`RF19_cierre_arqueo.md` §9) — "Sí, abrir caja" y "Sí, entregar" siguen pendientes de
   reprueba. De paso (A.8, consolidación RF08-RF19, Lote 1): "Ya hay una caja abierta desde
   el..." ahora es `CajaYaAbiertaException` (advertencia, no error) y "la caja sigue abierta: ciérrela..."
   (al entregar efectivo) es `CajaDeOtroDiaException` — ver la sección 6d, actualizada.
- El fondo inicial de la apertura no tiene un segundo registro de "quién lo entregó
  físicamente" (sección 2a) — no es un incumplimiento del texto (que no lo pide), pero queda
  anotado por si Joel quiere reforzarlo en el futuro.

## 11. Guion para la defensa

**Confirmado en pantalla el 11/10/2026 (sección 9), con el script de la sección 6e.**

1. Mostrar la apertura de caja: fondo sugerido precargado, editable, 0 también es válido.
2. Mostrar que el gerente solo puede VER `/caja` (no tiene el formulario de abrir/cerrar) y que
   el oficial no puede ni entrar.
3. Con el script de prueba cargado (sección 6e): mostrar el cartel rojo de "caja de otro día",
   que el formulario de entregar efectivo desaparece, y que cerrar esa caja la resuelve.
4. Mostrar el índice único de la base (`ux_sesiones_caja_una_abierta`) como la garantía final
   contra dos cajas abiertas a la vez, más allá del mensaje de la pantalla.
