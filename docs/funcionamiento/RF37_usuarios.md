# RF37 — Gestión de usuarios por el administrador

**Estado: cerrado. Probado en pantalla completo (09 y 10/10/2026, ver §9), con 3 correcciones
aplicadas y probadas el 10/10/2026 (ver §7 y §8). Tag `rf37-probado`.**

## 1. Qué exige el anteproyecto

| ID | Texto textual (`docs/RF_ANTEPROYECTO.md`) |
|---|---|
| RF37 | El sistema debe permitir al administrador crear usuarios, asignarles un perfil (administrador, gerente, oficial de crédito o cajera), y bloquearlos, inactivarlos, reactivarlos o restablecer su contraseña. |

Relacionado: RNF11 (permisos en el servidor — acá decide quién puede entrar a Usuarios).

## 2. Cómo funciona

| Caso | Qué ve el usuario | Qué pasa por dentro |
|---|---|---|
| Crear usuario (alta) | Formulario con nombres, apellidos, cédula y perfil (sin campo de contraseña); el mensaje de éxito muestra una contraseña temporal generada, una sola vez, y se exige cambiarla en el próximo ingreso | `ServicioUsuarios.alta`: normaliza el nombre de usuario (minúsculas, sin espacios), valida datos básicos, rechaza nombre de usuario y cédula duplicados, genera la temporal (`generarContrasenaTemporal`, `SecureRandom` — desde el 10/10/2026, antes dejaba fija "Cambiar@2026") e inserta |
| Asignar perfil | `<select>` con las 4 opciones: Administrador, Gerente, Oficial de crédito, Cajera | Enum `PerfilUsuario` (etiqueta "Cajera" desde el 10/10/2026 — el valor interno sigue siendo `CAJERO`) |
| Bloquear / Inactivar | Botón en la lista (oculto en la propia fila); pide confirmación | `ServicioUsuarios.bloquear`/`inactivar` → `cambiarEstadoConGuarda`: valida que no sea uno mismo y que no sea el último administrador activo |
| Reactivar | Botón en la lista (oculto en la propia fila, aunque reactivar nunca reduce la cantidad de administradores activos) | `ServicioUsuarios.reactivar`: sin guarda especial — reactivar siempre es seguro |
| Restablecer contraseña | Botón en la lista (oculto en la propia fila); el mensaje de éxito muestra la contraseña temporal | `ServicioUsuarios.restablecerContrasena`: genera una temporal (`SecureRandom`, sin caracteres ambiguos), exige cambiarla, y **si la cuenta estaba BLOQUEADA la desbloquea en el mismo paso** (hallazgo de Joel: si no, la contraseña nueva no serviría de nada hasta un segundo clic en "Reactivar") |
| Editar datos (nombres/apellidos/cédula/perfil) de otro usuario | Formulario precargado; el nombre de usuario no se puede tocar (deshabilitado) | `ServicioUsuarios.actualizar`: recorta espacios, valida, re-chequea la guarda de administradores si cambia el perfil, rechaza cédula duplicada (excepto la propia), audita solo los campos que realmente cambiaron |
| Editar el propio usuario | El `<select>` de perfil llega deshabilitado (no se puede cambiar el propio perfil desde ahí) | `resolverPerfilAUsar`: el servidor ignora lo que haya llegado (o no) en el perfil propuesto cuando quien edita es el mismo usuario — nunca confía en que el `<select>` deshabilitado alcance por sí solo |
| Alguien sin perfil ADMINISTRADOR entra a `/usuarios` (menú o URL a mano) | 403 | `FiltroAutorizacion`, permiso `ADMINISTRAR_USUARIOS` — TOTAL solo para ADMINISTRADOR (D-151); gerente, oficial y cajera quedan en NINGUNO |
| Crear un usuario con un nombre de usuario que ya existe | Mensaje claro: "Ese nombre de usuario ya existe. Elija otro." | `ServicioUsuarios.validarNombreUsuarioDisponible` (desde el 10/10/2026), llamado antes de insertar, sin distinguir mayúsculas |

## 3. Auditoría

| Evento | Cuándo |
|---|---|
| `ALTA_USUARIO` | Se crea un usuario nuevo |
| `ACTUALIZAR_USUARIO` | Se editan nombres/apellidos/cédula/perfil de otro usuario (o el propio, sin perfil) — el detalle lista solo los campos que cambiaron ("perfil (Gerente → Administrador)") |
| `BLOQUEAR_USUARIO` | Un administrador bloquea a otro usuario |
| `INACTIVAR_USUARIO` | Un administrador inactiva a otro usuario |
| `REACTIVAR_USUARIO` | Un administrador reactiva a otro usuario (manual, o automático dentro de `RESTABLECER_CONTRASENA` si la cuenta estaba bloqueada) |
| `RESTABLECER_CONTRASENA` | Un administrador restablece la contraseña de otro usuario |

Ninguno de estos eventos guarda la contraseña ni su hash (RNF08) — el mensaje de éxito con la
contraseña temporal se muestra solo una vez, como flash de sesión, nunca se persiste en
`auditoria_operaciones`.

## 4. Dónde está

- `servicios/ServicioUsuarios.java` — toda la lógica: `alta`, `actualizar`, `bloquear`,
  `inactivar`, `reactivar`, `restablecerContrasena`, `validarDatosBasicos`,
  `validarNombreUsuarioDisponible`, `validarPuedeBloquearseOInactivarse`,
  `validarPuedeCambiarPerfil`, `contarAdministradoresActivos`, `generarContrasenaTemporal`
  (desde el 10/10/2026, también la usa `alta`), `resolverPerfilAUsar`, `construirTextoCambios`.
- `dto/ResultadoAltaUsuario.java` (nuevo, 10/10/2026) — `idUsuario`/`contrasenaTemporal`, mismo
  patrón que `ResultadoRestablecerContrasena`.
- `dao/UsuarioDAO.java` — `insertar`, `actualizarDatos`, `actualizarEstadoCuenta`,
  `actualizarContrasena`, `buscarPorCedula`, `buscarPorNombreUsuario`, `contarAdministradoresActivos`.
- `controladores/ControladorUsuariosServlet.java` — `/usuarios` (lista), `/usuarios/nuevo`,
  `/usuarios/editar`, `/usuarios/bloquear`, `/usuarios/inactivar`, `/usuarios/reactivar`,
  `/usuarios/restablecer`, `/usuarios/informe`, `/usuarios/exportar`.
- `controladores/ControladorPanelServlet.java` — aviso de menos de 2 administradores activos
  (ver [procesos/login.md](procesos/login.md) §2.5).
- `filtros/FiltroAutorizacion.java` — permiso `ADMINISTRAR_USUARIOS`.
- `webapp/WEB-INF/vistas/usuarios/lista.jsp` / `formulario.jsp`.
- Tabla `usuarios` — `nombre_usuario` (UNIQUE simple + `ux_usuarios_nombre_usuario_minusculas`,
  índice único case-insensitive, D-152), `cedula_identidad` (UNIQUE), `perfil_usuario`,
  `estado_cuenta`.
- Enum `enumeraciones/PerfilUsuario.java` — `ADMINISTRADOR`, `GERENTE_ADMINISTRADOR` (etiqueta
  "Gerente"), `OFICIAL_CREDITO` ("Oficial de crédito"), `CAJERO` ("Cajera" — el valor interno
  sigue siendo `CAJERO`, solo cambió la etiqueta visible, 10/10/2026).

## 5. Dependencias

- **Depende de**: [procesos/login.md](procesos/login.md) (RF36/RNF08-12 — la cuenta que
  gestiona esta pantalla ya tuvo que loguearse; la contraseña inicial/temporal usa las mismas
  reglas de `validarContrasenaNueva`); RNF11 (permiso `ADMINISTRAR_USUARIOS`, D-151).
- **Lo afecta un cambio en**: `PerfilUsuario` (si se agrega/quita un perfil, el `<select>` y la
  matriz de `ServicioAutorizacion` hay que actualizarlos juntos).
- **A quién afecta un cambio acá**: [procesos/login.md](procesos/login.md) (la guarda de continuidad del
  administrador vive en este mismo servicio, `ServicioUsuarios`); RF38 (qué opciones ve cada
  perfil, depende de `PerfilUsuario`/la matriz); cualquier RF que registre `creado_por`/usuarios
  responsables de una operación (prácticamente todo el sistema).

## 6. Análisis

### 10/10/2026 — revisión completa de RF37 (pedido de Joel, solo lectura, sin cambios de código)

Joel pidió revisar cada parte del texto (crear, perfiles, bloquear, inactivar, reactivar,
restablecer, solo el administrador) y además: cédula y nombre de usuario repetidos; el último
administrador activo; no poder bloquearse ni inactivarse a sí mismo; validaciones del
formulario; qué queda en la auditoría en cada acción.

**Método**: lectura completa de `ServicioUsuarios.java`, `UsuarioDAO.java`,
`ControladorUsuariosServlet.java`, `lista.jsp`/`formulario.jsp`, `FiltroAutorizacion.java`
(mapeo de `/usuarios`), `PerfilUsuario.java`/`EstadoCuenta.java`.

**Resultado, parte del texto → cumple/no cumple/dónde:**

| Parte del texto | ¿Cumple? | Dónde |
|---|---|---|
| "permitir al administrador crear usuarios" | Cumple | `ServicioUsuarios.alta` + `POST /usuarios/nuevo`, protegido por `ADMINISTRAR_USUARIOS` (solo ADMINISTRADOR, D-151; probado por Joel: gerente recibe 403) |
| "asignarles un perfil (administrador, gerente, oficial de crédito o cajera)" | Cumple | Las 4 opciones existen (`PerfilUsuario`); la etiqueta visible del cuarto perfil era "Cajero" (masculino genérico) al momento del análisis — corregido a "Cajera" el 10/10/2026, ver §7 |
| "bloquearlos" | Cumple | `ServicioUsuarios.bloquear` → `cambiarEstadoConGuarda` |
| "inactivarlos" | Cumple | `ServicioUsuarios.inactivar` → misma guarda |
| "reactivarlos" | Cumple | `ServicioUsuarios.reactivar` (sin guarda especial — reactivar nunca reduce administradores activos) |
| "restablecer su contraseña" | Cumple, con una mejora propia | `ServicioUsuarios.restablecerContrasena`; además desbloquea la cuenta en el mismo paso si estaba BLOQUEADA (hallazgo de Joel, no exigido por el texto pero evita un segundo paso inútil) |
| (implícito) solo el administrador | Cumple | `FiltroAutorizacion`, `ADMINISTRAR_USUARIOS` — probado en pantalla (gerente: 403) |
| Cédula repetida | Cumple | `buscarPorCedula` en alta y en edición (excluyendo la propia fila al editar); mensaje claro: "Ya existe otro usuario con esa cédula." |
| Nombre de usuario repetido | Cumple | Al momento del análisis, `ServicioUsuarios.alta` no llamaba a `buscarPorNombreUsuario` antes de insertar (a diferencia de la cédula): la restricción `UNIQUE`/`ux_usuarios_nombre_usuario_minusculas` de la base rechazaba el `INSERT`, y el administrador veía "No se pudo completar la operación" en vez de un mensaje claro. Corregido el 10/10/2026: `validarNombreUsuarioDisponible` (pura) rechaza antes de insertar, con "Ese nombre de usuario ya existe. Elija otro." — ver §7. |
| El último administrador activo | Cumple | `contarAdministradoresActivos` + `validarPuedeBloquearseOInactivarse`/`validarPuedeCambiarPerfil`: no se puede bloquear, inactivar ni quitarle el perfil de administrador al último ADMINISTRADOR ACTIVO. Mensaje: "Debe quedar al menos un administrador activo." |
| No poder bloquearse ni inactivarse a sí mismo | Cumple, en el servidor | `validarPuedeBloquearseOInactivarse` rechaza si `objetivo.getIdUsuario().equals(solicitante.getIdUsuario())`, sin importar cómo llegó la petición; la lista además oculta esos botones en la propia fila (cortesía de la interfaz, no la protección real) |
| Validaciones del formulario | Cumple lo que el RF exige; sin exigencias propias sobre el nombre de usuario | `validarDatosBasicos`: nombres y apellidos obligatorios, cédula solo números, perfil válido. El nombre de usuario solo tiene `required` en el HTML — el servidor lo normaliza (minúsculas, sin espacios) pero no exige un largo mínimo ni rechaza símbolos; el RF no lo pide tampoco |
| Qué queda en la auditoría en cada acción | Cumple | Ver §3 — un evento distinto por cada acción, con el detalle de qué cambió en la edición |

**Observación aparte (no es parte del texto de RF37)**: no hay ninguna guarda en el servidor que
impida restablecerse la propia contraseña a uno mismo (`restablecerContrasena` no compara
`idUsuarioObjetivo` contra `idUsuarioSolicitante`) — la lista oculta el botón en la propia fila,
pero si alguien forzara el `POST /usuarios/restablecer` con su propio id, el servidor lo
permitiría. No es un riesgo real (ya puede cambiar su propia contraseña por el camino normal,
"Cambiar mi contraseña"), y el texto de RF37 no pide esta restricción — se anota solo para que
quede registrado, no como no cumplimiento.

**Conclusión**: RF37 está cumplido. Las dos observaciones menores encontradas en este análisis
(el rótulo "Cajero"/"cajera" y la falta de un mensaje claro para el nombre de usuario repetido)
se corrigieron el mismo día, antes de las pruebas en pantalla — ver §7 y §8.

## 7. Decisiones

- **D7** (anteproyecto entregado): perfil ADMINISTRADOR con acceso TOTAL a todos los permisos.
- **D7b**: la etiqueta visible de `GERENTE_ADMINISTRADOR` pasa a "Gerente" (para no confundirla
  con ADMINISTRADOR) — el nombre interno, la base y los permisos no se tocan.
- **D-151** (07/10/2026): Usuarios pasa a ser exclusivo del ADMINISTRADOR (el gerente pierde el
  acceso); la guarda "debe quedar al menos un administrador activo" deja de contar al gerente.
  Motivo de Joel: el administrador responde legalmente por el alta/baja/edición de usuarios y
  actúa en presencia del dueño.
- **Plan "Editar usuario"** (aprobado por Joel, antes del 09/10/2026): se agregó la edición de
  nombres/apellidos/cédula/perfil (no estaba en el texto literal de RF37, que solo habla de
  crear/bloquear/inactivar/reactivar/restablecer) — mismas validaciones y mismo mensaje de
  cédula duplicada que el alta; quien edita su propio usuario nunca puede cambiarse el perfil.
- **10/10/2026 (pedido de Joel, tras aprobar el análisis)**: corregidas las 2 observaciones de
  §6 antes de las pruebas en pantalla.
  1. **Nombre de usuario repetido**: se verifica antes de insertar (sin distinguir mayúsculas,
     igual que el índice `ux_usuarios_nombre_usuario_minusculas`), con el mensaje "Ese nombre de
     usuario ya existe. Elija otro." — motivo: dejar que la base lo rechazara con un
     `SQLException` genérico no le decía al administrador cuál era el problema.
  2. **Rótulo del perfil "Cajero" → "Cajera"**: el anteproyecto firmado dice "cajera" — se
     cambió la etiqueta visible del perfil `CAJERO` en `PerfilUsuario.java` (una sola línea, el
     resto de las pantallas/informes la heredan automáticamente a través de `getEtiqueta()`) y
     el único rótulo que estaba escrito a mano, el `<option>` de `formulario.jsp`. El valor
     interno (`CAJERO`, en la base y en el código) no se tocó, a pedido explícito de Joel.
- **10/10/2026 (hallazgo de Joel, probando en pantalla tras las 2 correcciones de arriba)**:
  toda cuenta nueva nacía con la misma contraseña fija "Cambiar@2026" (igual para todas, y
  escrita en el propio formulario de alta) — cualquiera que la conociera podía entrar a una
  cuenta recién creada antes que su dueño. Corregido: `ServicioUsuarios.alta` genera una
  temporal aleatoria con el mismo generador que `restablecerContrasena` (`generarContrasenaTemporal`,
  `SecureRandom`), devuelta en `ResultadoAltaUsuario` y mostrada una sola vez al administrador
  (mismo criterio que al restablecer: no se guarda en ningún lado, no se audita). El texto del
  formulario "La cuenta nace con la contraseña inicial Cambiar@2026" se reemplazó por "Al
  guardar, el sistema genera una contraseña temporal que se muestra una sola vez. La persona
  deberá cambiarla al entrar." La contraseña fija `CONTRASENA_INICIAL` ("Cambiar@2026") sigue
  existiendo en el código, pero solo para rechazarla si alguien intenta reusarla como contraseña
  nueva (`validarContrasenaNueva`) — ya no se asigna a ninguna cuenta nueva. Se revisó todo el
  repositorio: no queda en ninguna pantalla ni mensaje; sigue (sin tocar, a pedido de Joel) en
  `bd/03_parametros.sql` (contraseña semilla de `gerente`/`oficial`/`cajera`, como hash BCrypt,
  nunca en texto plano) y en comentarios de `bd/migraciones/D7_01_perfil_administrador.sql` y
  `docs/00_especificacion_maestra.md` (tabla de usuarios de prueba, §16.1).

## 8. Historial de cambios

| Fecha | Commit | Tag | Qué cambió |
|---|---|---|---|
| 16/11/2026 | *(este commit)* | — | **B222 probado en pantalla por Joel** (conteo y exportes arriba, sin la tarjeta de abajo) y corregido el defecto que encontró ahí: el resumen tenía un **espacio antes de cada coma**, que no venía del texto sino del salto de línea **entre** dos expresiones de JSP; ahora la lista se arma en el scriptlet y la pantalla imprime un solo valor. **RF37 queda cerrado**: lo único “no visto” de la pantalla de Usuarios es el cartel de **B217**, que es de /parametros |
| 15/11/2026 | *(este commit)* | — | **B222**: el listado de usuarios pasa al molde de los demás — conteo (total, por perfil y por estado) en la **barra de resultados** arriba de la tabla y los dos exportes a su derecha; arriba queda sola la acción primaria. La tarjeta de totales de abajo se borró |
| 15/11/2026 | *(este commit)* | — | **B213**: la contraseña temporal del alta y del restablecimiento se muestra en un modal que **no** se cierra con un clic afuera ni con Esc y trae **“Copiar”** — es el único momento en que se puede leer (§3: no se persiste en ninguna parte). **B214**: la lista explica la diferencia entre *“Bloquear”* (suspensión temporal; el mismo estado que deja `seguridad.intentos_login`) e *“Inactivar”* (baja definitiva: sella `fecha_inactivacion`, y restablecerle la contraseña **no** la reactiva), en una línea bajo el título y en el cuerpo de los dos carteles. Ver `FASE2_PLAN.md` § Seguridad — tanda 1 |
| 10/10/2026 | *(este commit)* | `rf37-probado` | Verificación de nombre de usuario repetido antes de insertar (`validarNombreUsuarioDisponible` + su test); etiqueta del perfil `CAJERO` cambiada de "Cajero" a "Cajera" (`PerfilUsuario.java` + `formulario.jsp`); alta de usuario genera una contraseña temporal aleatoria en vez de la fija "Cambiar@2026" (`ResultadoAltaUsuario`, `ServicioUsuarios.alta`, `ControladorUsuariosServlet`, texto de `formulario.jsp`) |
| 09/10/2026 | `d371dfb` | `login-consolidado` | Aviso en el panel con menos de 2 administradores activos; verificación de la guarda de continuidad (ya existía desde D-151); script `.bat` de recuperación |
| (sesión del 07/10/2026) | `02d302b` | — | D-151: Usuarios exclusivo del administrador; guarda de continuidad deja de contar al gerente |

## 9. Pruebas realizadas en pantalla

**09 y 10/10/2026, primera ronda (Joel, en el navegador):** bloquear, reactivar, restablecer
contraseña (y que desbloquea si la cuenta estaba bloqueada); solo el administrador tiene acceso
(el gerente recibe 403 al entrar a `/usuarios`); la propia cuenta en la lista solo muestra
"Editar" (Bloquear/Inactivar/Restablecer quedan ocultos). **Resultado: todo OK.**

**10/10/2026, segunda ronda, después del Clean and Build (Joel, en el navegador):** rótulo
"Cajera" en la lista y en el informe PDF; nombre de usuario repetido (aun escrito en mayúsculas)
con el mensaje claro; cédula repetida ("Ya existe otro usuario con esa cédula"); alta de un
administrador nuevo (`ana.prueba`) y el aviso del panel desaparece al llegar a 2 administradores
activos; edición de otro usuario; informe PDF con totales por perfil y por estado; inactivar.
**Resultado: todo OK** — encontró, probando el alta, el hallazgo de la contraseña fija corregido
en §7/§8 (ver abajo).

**10/10/2026, tercera ronda, después del Clean and Build (Joel, en el navegador):** alta de un
usuario nuevo — el mensaje de éxito mostró una contraseña temporal aleatoria (ej.
`TempQ6ciFrrZ1`), una sola vez, y el formulario ya no menciona ninguna contraseña fija.
**Resultado: todo OK. RF37 queda probado completo.**

Sin probar todavía: el CSV de Usuarios (`/usuarios/exportar`) — el PDF (`/usuarios/informe`) ya
se probó con los totales por perfil y por estado.

**13/10/2026 (Joel), después de Clean and Build — segundo administrador real:** alta de
`administrador2` (Patricia Gómez, C.I. 9100111) con perfil ADMINISTRADOR. Al ingresar por
primera vez, el sistema obligó el cambio de contraseña (RF36, primer ingreso) y el aviso
"Recomendado: tener al menos 2 administradores" desapareció del panel al quedar 2 cuentas
ADMINISTRADOR activas. **Resultado: OK** — a diferencia de la prueba del 10/10/2026 (línea
arriba, con `ana.prueba`, una cuenta de prueba pendiente de limpieza en `AVANCE.md`), esta
cuenta es la que cumple de verdad el pendiente "2 administradores" de `AVANCE.md` antes de
entregar (ver también RF36, `procesos/login.md` §9 sobre el mismo aviso).

**14/10/2026, Tanda 2, probado en pantalla por Joel** — tag `mejoras-tanda2-probado`: nuevo
usuario "aprueba" con cédula tecleada "9.100.222" → guardada como "9100222" (sin puntos). **OK**
— confirma `ServicioUsuarios.alta`/`actualizar` reutilizando `ServicioClientes.normalizarCedula`
(Ajuste B). El aviso con "!" en vez de X roja (mismo punto de §10, abajo) no se probó con un
caso de rechazo específico en esta tanda — se validó en Cotizador (ver `RF01_cotizador.md` §9),
mismo mecanismo.

## 10. Pendientes y trabajo futuro

- ~~**(13/10/2026, Joel, Tanda 2 — anotar, no implementar)**: en "Nuevo usuario", la cédula con
  puntos (ej. "9.100.111") se rechaza con "La cédula debe contener solo números"~~ — **resuelto
  y probado en pantalla el 14/10/2026** (ver §9): acepta la cédula con o sin puntos y la guarda
  siempre sin ellos. **También resuelto (no probado con un caso de rechazo específico en
  Usuarios, ver §9)**: el rechazo de datos mal cargados en "Nuevo usuario"/"Editar usuario" ya
  sale como advertencia ("!", "Dato incorrecto.") en vez de error (X roja) —
  `ControladorUsuariosServlet` ahora usa `mensajeAdvertencia`, mismo mecanismo ya corregido en
  RF39 (2a).
- Pendiente menor, sin prioridad: probar el CSV de Usuarios (ver §9). El resto de RF37 (§6 y el
  hallazgo de la contraseña fija de §7) ya se aplicó y se probó en pantalla.

## 11. Guion para la defensa

1. Como administrador, crear un usuario de prueba, mostrar la contraseña temporal generada (una
   sola vez, distinta cada vez) y que exige cambiarla.
2. Asignarle cada uno de los 4 perfiles (editar, cambiar el `<select>`) y mostrar la matriz de
   permisos resultante en el menú de esa cuenta.
3. Bloquearlo, mostrar que no puede entrar; reactivarlo.
4. Inactivarlo, mostrar que tampoco puede entrar.
5. Restablecerle la contraseña estando bloqueado — mostrar que un solo paso lo desbloquea y le
   da una contraseña nueva.
6. Con una cuenta sin el perfil de administrador, mostrar el 403 al entrar a `/usuarios` (menú y
   URL a mano).
7. Intentar bloquear o cambiarle el perfil al único administrador activo que quede en la demo —
   mostrar "Debe quedar al menos un administrador activo."
8. Mostrar que la propia cuenta, en la lista, solo tiene "Editar" (sin Bloquear/Inactivar/
   Restablecer).
9. Abrir el informe PDF y el CSV de Usuarios y accesos, mostrando que nunca aparece ninguna
   contraseña ni su hash.
