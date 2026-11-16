# Login, seguridad de acceso y continuidad del administrador

RF36, RF37 (gestión de usuarios: ver también [RF37_usuarios.md](../RF37_usuarios.md)), RNF08,
RNF09, RNF10, RNF11, RNF12. Armado desde el código actual de la rama
`d149-compromisos-anteproyecto`, al cierre del tag `login-consolidado`.

## 1. Qué exige el anteproyecto

| ID | Texto textual (`docs/RF_ANTEPROYECTO.md`) |
|---|---|
| RF36 | El sistema debe permitir el ingreso con usuario y contraseña, y obligar a cambiar la contraseña en el primer ingreso (mínimo 8 caracteres, con al menos una letra y un número). |
| RNF08 | El sistema debe guardar las contraseñas cifradas, de forma que nadie pueda leerlas, ni siquiera desde la base de datos. |
| RNF09 | El sistema debe bloquear la cuenta después de 3 intentos fallidos de ingreso y mostrar el mismo mensaje sin revelar si el usuario existe. |
| RNF10 | El sistema debe cerrar la sesión después de 30 minutos de inactividad, avisando al usuario 2 minutos antes. |
| RNF11 | El sistema debe controlar los permisos de cada perfil en el servidor, aunque el usuario escriba la dirección de una pantalla a mano, y rechazar formularios enviados desde fuera del sistema. |
| RNF12 | El sistema no debe permitir borrar operaciones registradas. Las correcciones se hacen por anulación o reversión, con motivo, autorización del gerente y registro en la auditoría. |

(RF37, la gestión de usuarios por el administrador, tiene su propio texto y análisis en
[RF37_usuarios.md](../RF37_usuarios.md) — acá solo se la menciona donde se cruza con el login:
restablecer contraseña, bloquear/inactivar/reactivar.)

## 2. Cómo funciona

### 2.1 Casos del ingreso

| Caso | Qué ve el usuario | Qué pasa por dentro |
|---|---|---|
| Usuario que no existe | Mensaje único de rechazo (ver 2.2), sin decir que el usuario no existe | `ServicioAutenticacion.autenticar` no encuentra el usuario; se calcula un BCrypt contra un hash de relleno (`HASH_DUMMY_PARA_USUARIO_INEXISTENTE`) para tardar lo mismo que un intento real |
| Contraseña incorrecta (cuenta ACTIVA, no llega al límite) | Mismo mensaje único | `intentos_fallidos` + 1 |
| Contraseña incorrecta, llega a `seguridad.intentos_login` (3) intentos | Mismo mensaje único (no dice que se acaba de bloquear) | `estado_cuenta` → BLOQUEADA, `intentos_fallidos` queda en 3 |
| Cuenta ya estaba BLOQUEADA (dentro de los 15 min, o bloqueo manual) | Mismo mensaje único, aunque la contraseña sea correcta — ni siquiera se evalúa | `intentarAcceso` corta antes de llamar a `BCrypt.checkpw` |
| Cuenta BLOQUEADA automáticamente, ya pasaron `seguridad.minutos_bloqueo` (15) y la contraseña es correcta | Entra directo, sin pasos extra | `desbloquearPorTiempoSiCorresponde` libera el bloqueo en la misma transacción, antes de evaluar la contraseña |
| Cuenta BLOQUEADA manualmente (el administrador la bloqueó desde Usuarios) | Mismo mensaje único, nunca se libera sola aunque pase cualquier cantidad de tiempo | `debeDesbloquearPorTiempo` compara la fecha del último `BLOQUEAR_USUARIO` contra la del último `CUENTA_BLOQUEADA`: si el manual es igual o más reciente, no libera |
| Cuenta INACTIVA | Mismo mensaje único, aunque la contraseña sea correcta | No se toca `intentos_fallidos` (no tiene sentido bloquear algo ya inactivo) |
| Usuario y contraseña correctos, primer ingreso o tras un restablecimiento | Redirige a "Cambiar contraseña" (obligatorio, sin acceso a ninguna otra pantalla hasta cambiarla) | `debe_cambiar_contrasena = true` |
| Usuario y contraseña correctos, normal | Entra al panel (o a la página interna que había pedido antes de que lo mandaran a loguearse) | `FiltroAutenticacion` había armado `?destino=...`; `ControladorAutenticacionServlet` valida que sea una ruta interna antes de redirigir ahí |
| Base de datos caída durante el login | "No se pudo conectar con el sistema. Intente nuevamente." (nunca el detalle técnico) | `SQLException` capturada, detalle solo al log del servidor |

### 2.2 El mensaje único (RNF09)

Un solo texto, igual para los 5 casos que antes se distinguían (usuario inexistente, contraseña
incorrecta, cuenta recién bloqueada, cuenta ya bloqueada, contraseña correcta pero bloqueada):

> *"Usuario o contraseña incorrectos. Por seguridad, después de 3 intentos fallidos la cuenta se
> bloquea por 15 minutos. Si olvidó su contraseña, pídasela a un administrador."*

Con los números leídos de los parámetros (`seguridad.intentos_login`/`seguridad.minutos_bloqueo`),
nunca escritos en el código — es información general de la política, no un dato de la cuenta que
se está probando (nunca su conteo real de intentos, ni si está bloqueada en ese momento).

### 2.3 Cambio de contraseña

| Caso | Qué ve el usuario | Qué pasa por dentro |
|---|---|---|
| La actual está mal | Mensaje de error, sin tocar `intentos_fallidos` del login (es un circuito aparte) | `validarContrasenaActual` |
| La actual mal, 3 veces seguidas en la misma sesión | Se cierra la sesión y vuelve a `/acceso` con un aviso ("se cerró la sesión por varios intentos fallidos") | `ControladorCambioContrasenaServlet` cuenta en la sesión (atributo propio, reusa `seguridad.intentos_login`) |
| Nueva menor a 8 caracteres, sin letra+número, igual a la inicial, igual a la actual, o **contiene el nombre de usuario** | Mensaje de error específico de cada regla | `ServicioUsuarios.validarContrasenaNueva` — no se guarda nada, se rechaza antes |

### 2.4 Sesión

| Caso | Qué ve el usuario | Qué pasa por dentro |
|---|---|---|
| Cerrar sesión (botón "Salir") | Vuelve a `/acceso` | `invalidate()`, marcado antes como cierre voluntario |
| Sesión vence sola por 30 min de inactividad | La próxima petición lo manda a `/acceso` como si nunca hubiera entrado; 2 minutos antes de vencer, un aviso en pantalla ofrece seguir conectado | `ListenerSesion` audita al destruirse la sesión, salvo que ya estuviera marcada como cierre voluntario |
| Página interna sin sesión (URL escrita a mano, o sesión ya vencida) | Redirige a `/acceso` y, tras loguearse, vuelve a esa misma página | `FiltroAutenticacion` arma `?destino=` solo para GET; valida que sea una ruta interna de `/sgcpc`, nunca una URL absoluta |
| Botón "atrás" del navegador después de salir | No muestra ninguna pantalla interna ya cacheada | `Cache-Control: no-store, no-cache, must-revalidate` + `Pragma`/`Expires` en toda ruta con sesión |

### 2.5 Recuperación de acceso y continuidad del administrador

Por D-151, Usuarios es exclusivo del ADMINISTRADOR. De menor a mayor gravedad:

1. **Alguien olvidó su contraseña**: lo resuelve cualquier administrador activo desde Usuarios
   → "Restablecer contraseña" (genera una temporal, exige cambiarla, y si la cuenta estaba
   bloqueada la desbloquea en el mismo paso).
2. **El segundo administrador**: por eso conviene tener siempre al menos 2 administradores
   ACTIVOS — el panel avisa cuando quedan menos de 2. El sistema ya impide bloquear, inactivar o
   quitarle el perfil de administrador al último administrador activo
   (`ServicioUsuarios.validarPuedeBloquearseOInactivarse`/`validarPuedeCambiarPerfil`, mensaje:
   "Debe quedar al menos un administrador activo."). Detalle completo en
   [RF37_usuarios.md](../RF37_usuarios.md).
3. **Cuenta de emergencia en sobre cerrado**: recomendación operativa (no es código) — una
   cuenta ADMINISTRADOR adicional, de uso exclusivo para emergencias, con sus credenciales
   guardadas en un sobre cerrado por el dueño. Usarla queda auditada igual que cualquier acceso;
   hay que cambiarle la contraseña enseguida después de usarla.
4. **Script de recuperación, último recurso, fuera de la aplicación**: `bd/desbloquear_usuario.sh`
   (Git Bash) o `bd/desbloquear_usuario.bat` (CMD/PowerShell, requiere `entorno.bat` antes).
   Reactiva la cuenta sin tocar la contraseña; con `--restablecer`/`/restablecer` además genera
   una temporal nueva y anota el motivo en `bd/desbloquear_usuario.log`.

El detalle completo (advertencias, la consulta SQL directa si ni el script alcanza) está en
`LEEME.md` (de `sistema/`, no de esta carpeta), sección "Recuperación de acceso".

## 3. Auditoría

| Evento | Cuándo |
|---|---|
| `ACCESO_USUARIO_INEXISTENTE` | Intento de acceso con un nombre de usuario que no existe (`id_usuario` NULL, con `usuario_escrito`) |
| `ACCESO_FALLIDO` | Contraseña incorrecta, cuenta ACTIVA, no llega al límite |
| `CUENTA_BLOQUEADA` | Contraseña incorrecta y se llega al límite de intentos (bloqueo automático) |
| `ACCESO_CUENTA_BLOQUEADA` | Intento de acceso a una cuenta ya bloqueada (automática o manual) |
| `ACCESO_CUENTA_INACTIVA` | Intento de acceso a una cuenta inactiva |
| `CUENTA_DESBLOQUEADA_AUTOMATICAMENTE` | Se liberó un bloqueo automático por haber pasado `seguridad.minutos_bloqueo` |
| `ACCESO_CONCEDIDO` | Login correcto (con o sin cambio de contraseña pendiente) |
| `CAMBIAR_CONTRASENA` | Cambio de contraseña propia exitoso |
| `CAMBIAR_CONTRASENA_FALLIDO` | Cambio de contraseña propia rechazado por contraseña actual incorrecta |
| `CERRAR_SESION` | Cierre de sesión voluntario (botón "Salir") |
| `CERRAR_SESION_POR_INTENTOS_FALLIDOS` | Sesión cerrada tras 3 fallos seguidos de la contraseña actual al cambiarla |
| `SESION_EXPIRADA` | La sesión se destruye sola (30 min de inactividad), sin haber pasado por un cierre voluntario |
| `ALTA_USUARIO` / `ACTUALIZAR_USUARIO` / `BLOQUEAR_USUARIO` / `INACTIVAR_USUARIO` / `REACTIVAR_USUARIO` / `RESTABLECER_CONTRASENA` | Acciones del administrador sobre otro usuario (RF37) — detalle en [RF37_usuarios.md](../RF37_usuarios.md) |

Ninguna fila de `auditoria_operaciones` contiene nunca una contraseña ni su hash (RNF08) — el
propio `AuditoriaDAO` no sabe qué es una contraseña, así que esa responsabilidad es de quien
arma cada registro (verificado leyendo cada llamador: ninguno pasa `contrasena_hash` ni el texto
tipeado de ninguna contraseña). RNF12: la tabla tiene triggers `trg_bloquear_eliminacion`/
`trg_bloquear_actualizacion` que rechazan cualquier `UPDATE`/`DELETE`, incluso para el propio
dueño de la base.

## 4. Dónde está

**Servicios**
- `servicios/ServicioAutenticacion.java` — `intentarAcceso` (puro), `autenticar` (orquesta con
  transacción y auditoría), `debeDesbloquearPorTiempo`/`desbloquearPorTiempoSiCorresponde`,
  `cambiarContrasenaPropia`, `validarContrasenaActual`, `mensajeAccesoRechazado`.
- `servicios/ServicioUsuarios.java` — `validarContrasenaNueva`, `validarPuedeBloquearseOInactivarse`,
  `validarPuedeCambiarPerfil`, `contarAdministradoresActivos`, `bloquear`/`inactivar`/`reactivar`,
  `restablecerContrasena`, `generarContrasenaTemporal` (detalle completo en
  [RF37_usuarios.md](../RF37_usuarios.md)).
- `servicios/ServicioAuditoria.java` / `dao/AuditoriaDAO.java` — registran y listan
  `auditoria_operaciones` (filtros, totales, reporte).

**Controladores y filtros**
- `controladores/ControladorAutenticacionServlet.java` — `/acceso` (GET/POST), `/salir`.
- `controladores/ControladorCambioContrasenaServlet.java` — `/cambiar-contrasena`.
- `controladores/ControladorUsuariosServlet.java` — alta, edición, bloquear, inactivar,
  reactivar, restablecer contraseña (RF37, exclusivo del ADMINISTRADOR desde D-151).
- `controladores/ControladorPanelServlet.java` — aviso de menos de 2 administradores activos.
- `controladores/ControladorSesionServlet.java` — `/sesion/renovar` (RNF10, "seguir conectado").
- `filtros/FiltroAutenticacion.java` — exige sesión, redirige con `?destino=`, fuerza
  `/cambiar-contrasena` mientras esté pendiente, cabeceras `Cache-Control`.
- `filtros/FiltroAutorizacion.java` — RNF11, permisos por perfil en el servidor.
- `filtros/FiltroCsrf.java` — RNF11, rechaza formularios sin el token de la sesión.
- `filtros/ListenerSesion.java` — audita `SESION_EXPIRADA`.

**Pantallas**
- `acceso.jsp`, `cambiar-contrasena.jsp`, `panel.jsp` (aviso de continuidad),
  `reportes/auditoria.jsp`.
- `recursos/js/aviso-sesion.js` (RNF10), `recursos/js/campo-contrasena.js` (ojo
  mostrar/ocultar y aviso de Bloq Mayús, en las 3 pantallas con contraseña).

**Base de datos**
- `usuarios.estado_cuenta` (ACTIVA/BLOQUEADA/INACTIVA), `usuarios.contrasena_hash` (BCrypt,
  RNF08), `usuarios.intentos_fallidos`, `usuarios.debe_cambiar_contrasena`.
- `auditoria_operaciones` — `id_usuario` (NULL-able desde D-152), `direccion_ip`, `navegador`,
  `usuario_escrito`, con triggers `trg_bloquear_eliminacion`/`trg_bloquear_actualizacion`.
- `bd/migraciones/E10_11_seguridad_auditoria.sql` (aplicada solo a `sgcpc_db_pruebas`, también
  incorporada a `01_esquema.sql`/`02_indices.sql`).

**Parámetros (`parametros_sistema`, editables solo por el ADMINISTRADOR desde Parámetros)**
| Parámetro | Valor actual | Uso |
|---|---|---|
| `seguridad.intentos_login` | 3 | Intentos fallidos antes de bloquear (RNF09) |
| `seguridad.minutos_bloqueo` | 15 | Minutos para liberar un bloqueo automático (RNF09) |
| `seguridad.minutos_inactividad` | 30 | Minutos de inactividad antes de cerrar la sesión (RNF10) |

**Cookies y sesión (`web.xml`)**: `session-timeout` 30 minutos; cookie de sesión `HttpOnly`
(no se puede leer desde JavaScript). Falta el flag `Secure` — ver pendientes (§10).

## 5. Dependencias

- **Depende de**: nada funcionalmente previo — es la base de todo el sistema (E0/E2). El
  parámetro `seguridad.*` depende de RF39 (Parámetros, con su historial de cambios).
- **Lo afecta un cambio en**: `ServicioAuditoria`/`AuditoriaDAO` (cualquier cambio en la forma de
  auditar impacta también a RF40). `PerfilUsuario`/`ServicioAutorizacion` (RF38/RNF11 — la
  matriz de permisos decide qué puede hacer cada perfil, login solo abre la sesión).
- **A quién afecta un cambio acá**: a **todo** el sistema — `FiltroAutenticacion` y
  `FiltroAutorizacion` corren en cada petición de cualquier RF (RNF11); `ServicioAuditoria` la
  usan todos los servicios de negocio (RNF12). RF37 (gestión de usuarios) depende directo de la
  guarda de continuidad del administrador definida acá. RF40 (auditoría) depende de las columnas
  `direccion_ip`/`navegador`/`usuario_escrito` agregadas en D-152.

## 6. Análisis

### 10/10/2026 — revisión de la estructura de este documento

Sin análisis nuevo de código ese día para el login en sí — se reorganizó este documento a la
plantilla de 11 secciones (pedido de Joel) y se separó RF37 en su propio documento
([RF37_usuarios.md](../RF37_usuarios.md)).

### Antes del 09/10/2026 — "cómo funciona el login en todos los casos posibles"

Joel pidió una revisión completa de los casos del login (usuario en mayúsculas/espacios,
contraseña en mayúsculas/espacios, reinicio del contador, cuenta INACTIVA, primer ingreso,
restablecer por el administrador, sesión de 30 min/cerrar/atrás/dos navegadores, página interna
sin sesión, base caída, auditoría en cada caso), marcando cualquier caso que no cumpliera RF36,
RF37 o RNF09, sin corregir nada todavía. Ese análisis llevó directo al bloque de trabajo D-152
(§7 de abajo) — su contenido punto por punto no quedó guardado textualmente en ningún archivo
en ese momento (esta carpeta de documentación no existía todavía); la tabla de casos de la
sección 2 de este documento es su versión ya actualizada, con las correcciones ya aplicadas.

## 7. Decisiones

- **D-27** (E2): primera versión de la auditoría de acceso — ya distinguía eventos, pero sin
  `direccion_ip`/`navegador`/`usuario_escrito` y sin auditar usuarios inexistentes.
- **D-45**: mensaje de error con el límite general de intentos (nunca un contador real por
  cuenta), más el botón de ojo y el aviso de Bloq Mayús.
- **D-151** (07/10/2026): Usuarios pasa a ser exclusivo del ADMINISTRADOR (el gerente pierde el
  acceso); la guarda "debe quedar al menos un administrador activo" deja de contar al gerente.
- **D-152** (09/10/2026, tag `login-consolidado`): bloqueo temporal (15 min) además del manual;
  mensaje único para los 5 casos, con tiempo de respuesta igualado; `ACCESO_USUARIO_INEXISTENTE`
  empieza a auditarse; columnas `direccion_ip`/`navegador`/`usuario_escrito`; `SESION_EXPIRADA`
  con `ListenerSesion`; cookie `HttpOnly`.
- **Sin número** (09-10/10/2026, mismo tag): guarda de continuidad del administrador verificada
  (ya existía desde D-151) + aviso en el panel cuando quedan menos de 2 administradores activos;
  script `.bat` de recuperación para Windows; `auditoria.jsp` con las columnas nuevas legibles
  (LEFT JOIN en vez de INNER JOIN, Id "—" sin entidad real, IP local como "Este equipo
  (servidor)", navegador resumido); la contraseña nueva ya no puede *contener* el nombre de
  usuario (antes solo rechazaba la igualdad exacta).
- **10/10/2026**: se decide documentar por RF/proceso en `docs/funcionamiento/`, con una
  plantilla obligatoria de 11 secciones (quién: Joel; por qué: mantener la documentación
  sincronizada con el código real, por RF, no solo en `AVANCE.md`).
- **10/10/2026 (hallazgo de Joel probando RF04 en pantalla, revisión de los carteles del
  sistema)**: el cartel "Sesión vencida" (`recursos/js/aviso-sesion.js`) usaba el botón por
  defecto de SweetAlert2 ("OK") en vez de un texto propio — se le agregó `confirmButtonText:
  'Entendido'`, igual que el resto de los carteles del sistema (ver [RF04_buro.md](../RF04_buro.md)
  §7 para el resto de los carteles revisados).

Detalle completo de cada una en `AVANCE.md` (sesiones del 07, 09 y 10/10/2026).

## 8. Historial de cambios

| Fecha | Commit | Tag | Qué cambió |
|---|---|---|---|
| 15/11/2026 | *(este commit)* | — | **B216**: en */cambiar-contrasena*, el campo de confirmación avisa **en vivo** si no coincide con la contraseña nueva y no deja enviar hasta que coincidan (`data-confirma-a` + `recursos/js/campo-contrasena.js`), así un error de tipeo ya no cuesta un viaje al servidor con los tres campos vaciándose. El mostrar/ocultar y el aviso de Bloq Mayús **ya existían** en ese mismo script. Los campos **no** se rellenan tras un rechazo, a propósito (RNF08). La validación del servidor no cambia. Ver `FASE2_PLAN.md` § Seguridad — tanda 1 |
| 09/10/2026 | `d371dfb` | `login-consolidado` | Bloqueo temporal + mensaje único + auditoría legible (IP/navegador/usuario escrito, Id "—", IP local, navegador resumido) + continuidad del administrador (aviso en el panel, script `.bat`, LEEME.md) + contraseña que no puede contener el usuario + corrección de numeración de RNF en todo el repositorio |
| 09/10/2026 | `76310f8` | `rnf09-bloqueo-probado` | RNF09: mensaje único de acceso rechazado, tiempo de respuesta igualado, cookie `HttpOnly` |
| 09/10/2026 | `02d302b` | — | RF36/RF37: bloqueo temporal, hash propio del administrador y textos de D-151 (antes de probar en pantalla) |

## 9. Pruebas realizadas en pantalla

**09/10/2026 (Joel, en el navegador):** mayúsculas en el nombre de usuario; aviso de "menos de 2
administradores activos" en el panel; el nombre de usuario se mantiene al fallar y aviso de Bloq
Mayús; usuario inexistente queda en la auditoría; vuelve a la página interna que había pedido;
restablecer contraseña desbloquea la cuenta; primer ingreso obligatorio; reglas de contraseña
(igual a la actual, menos de 8 caracteres, confirmación que no coincide). **Resultado: todo OK.**

**10/10/2026 (Joel, en el navegador, después del Clean and Build):** auditoría legible ("—",
"Este equipo (servidor)", "Chrome · Windows"); contraseña que contiene el nombre de usuario,
rechazada; 3 fallos de la contraseña actual en "Cambiar contraseña" cierran la sesión; después de
salir, el botón "atrás" no muestra datos cacheados; bloqueo manual: no entra ni con la contraseña
correcta, mismo mensaje; cuenta inactiva: no entra ni con la contraseña correcta, mismo mensaje.
**Resultado: todo OK.**

## 10. Pendientes y trabajo futuro

- **HTTPS + cookie `Secure`**: hoy la cookie de sesión es `HttpOnly` pero no `Secure` (no tiene
  sentido marcarla `Secure` sin HTTPS — el navegador la descartaría). Cuando el servidor de
  defensa/producción tenga HTTPS, agregar `<secure>true</secure>` en `web.xml` junto al
  `http-only` que ya existe.
- **PDF/CSV del reporte de Auditoría**: la pantalla ya muestra IP/navegador/usuario escrito de
  forma legible ("Este equipo (servidor)", "Chrome · Windows", etc.); el PDF y el CSV todavía
  muestran solo las columnas viejas. Queda para cuando se revise el RF40 completo, junto con dos
  pendientes más anotados ese día en `AVANCE.md`: nombres legibles de las operaciones (hoy se ve
  el nombre interno, ej. `ACCESO_USUARIO_INEXISTENTE`) y que `RECALCULO_MORA_DIARIO` figure como
  "Sistema" en vez del primer usuario que entró ese día.
- **Columna "Detalle" cortada** (hallazgo de Joel, prueba de pantalla del RF08, 11/10/2026): en
  `reportes/auditoria.jsp` la celda de Detalle no ajusta el texto largo — se ve cortada. El dato
  en sí no se pierde (`auditoria_operaciones.detalle` es `TEXT`, sin límite; se confirmó
  leyendo `bd/01_esquema.sql` línea 641), es un problema de presentación (la tabla no envuelve
  el texto de esa columna). Queda para cuando se revise el RF40 completo.
- **Las operaciones que no son de inicio de sesión no registran IP ni navegador** (mismo
  hallazgo, misma fecha): `ServicioAuditoria.registrar(conexion, idUsuario, operacion,
  tipoEntidad, idEntidad, detalle)` (la variante de 6 parámetros, la que usan
  `APROBAR_SOLICITUD`/`RECHAZAR_SOLICITUD`/`CANCELAR_SOLICITUD` y el resto de las operaciones de
  negocio) pasa `null` para IP y navegador — solo existe una variante con esos dos datos
  ("Bloque de seguridad... para los eventos de acceso y de sesión") y ningún llamador de fuera
  del login la usa. No es un error de este RF08 ni algo que haya cambiado — es así desde que se
  armó la auditoría; queda anotado para que Joel decida si vale la pena capturarlos también en
  las operaciones de negocio (requeriría pasar el `HttpServletRequest` a cada
  `ServicioXxx.operacion(...)`, hoy sin esa dependencia) cuando se revise el RF40 completo.
- **Parámetro `destino` doblemente codificado** (hallazgo de Joel, 16/10/2026): se observó
  `localhost:8081/sgcpc/acceso?destino=%2Fsgcpc%2Facceso%25C3%25A7` — decodificando una vez da
  `/sgcpc/acceso%C3%A7`, decodificando otra vez da `/sgcpc/accesoç` (una ruta que no es
  exactamente `/acceso`, con un carácter no ASCII de más). Causa: `FiltroAutenticacion.
  construirRedirectAAcceso` (líneas 98-110) hace `URLEncoder.encode(httpRequest.getRequestURI()
  + query, "UTF-8")`, pero `getRequestURI()` devuelve la URL sin decodificar tal como la mandó
  el cliente — si esa URL ya traía un carácter no ASCII (codificado por el navegador como
  `%XX`), ese `%` se vuelve a codificar entero (`%` a `%25`), doblando la codificación. Como la
  ruta resultante (`/sgcpc/accesoç`) no coincide con la ruta pública `/acceso`, el filtro la trata
  como protegida y dispara esta redirección armada mal, apuntando a la propia página de acceso.
  Sin corregir todavía — queda para decidir si se decodifica `getRequestURI()` antes de
  reconstruir `destino`, o se usa otra fuente ya decodificada.

## 11. Guion para la defensa

Una corrida de ~5 minutos que muestra RF36 y los 5 RNF de este documento sin necesitar
preparación previa (usa las cuentas de siempre, `gerente`/`administrador`/`cajera`):

1. **Ingreso normal**: loguearse con una cuenta cualquiera, mostrar que redirige al panel.
2. **Primer ingreso / contraseña obligatoria**: con una cuenta recién restablecida (o explicar
   el caso), mostrar que no deja avanzar a ninguna pantalla hasta cambiar la contraseña, y que
   la nueva exige 8 caracteres, letra + número, y rechaza una que contenga el nombre de usuario.
3. **RNF09, mensaje único**: probar con un usuario que no existe y con una contraseña
   incorrecta de una cuenta real — mismo texto en los dos casos. Fallar 3 veces seguidas con una
   cuenta de prueba: se bloquea, mismo mensaje otra vez (no dice "se bloqueó").
4. **Bloqueo manual vs. automático**: mostrar en Usuarios que un administrador puede bloquear a
   mano (nunca se libera solo), contra el bloqueo automático de arriba (se libera a los 15
   minutos, o el administrador lo reactiva antes desde Usuarios).
5. **Continuidad del administrador**: intentar bloquear o cambiarle el perfil al único
   administrador activo que quede en la demo — mostrar el mensaje "Debe quedar al menos un
   administrador activo." y, si hay menos de 2, el aviso del panel.
6. **RNF10, sesión**: mencionar el aviso 2 minutos antes de los 30 de inactividad (no hace
   falta esperar los 30 minutos reales en la defensa); mostrar `/sesion/renovar` en las
   herramientas del navegador si se pide algo más técnico.
7. **RNF11, permisos en el servidor**: con una cuenta sin el perfil de administrador, escribir a
   mano la URL de Usuarios — mostrar que lo rechaza aunque no se vea el botón en el menú.
8. **RNF08/RNF12, auditoría e integridad**: abrir el reporte de Auditoría, mostrar las columnas
   de IP/navegador/usuario escrito recién agregadas, y explicar que ninguna fila contiene nunca
   una contraseña ni su hash, y que la tabla no permite `UPDATE`/`DELETE` (ni para el propio
   dueño de la base).
9. Si preguntan por HTTPS: señalar el pendiente de la cookie `Secure` (§10) como trabajo futuro,
   explícitamente fuera del alcance de esta demo (sin servidor HTTPS en este entorno).
10. Para RF37 (gestión de usuarios por el administrador), seguir con el guion de
    [RF37_usuarios.md](../RF37_usuarios.md).
