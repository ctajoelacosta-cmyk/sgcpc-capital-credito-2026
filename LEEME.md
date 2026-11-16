# LEEME — SGCPC

## Instalación en el servidor

- Si se configura HTTPS, agregar `<secure>true</secure>` en el `<cookie-config>` de `web.xml`
  (junto al `<http-only>true</http-only>` que ya está) — mientras todo corra por HTTP plano, no
  agregarlo: el navegador no manda una cookie `Secure` por una conexión sin cifrar y rompería la
  sesión.
- **Migraciones nuevas** (bloque de seguridad/auditoría): correr contra la base de destino antes
  de usar la versión nueva del `.war` —
  `bd/migraciones/E10_10_parametro_minutos_bloqueo.sql` y
  `bd/migraciones/E10_11_seguridad_auditoria.sql` (agrega `navegador`/`usuario_escrito` a
  `auditoria_operaciones`, afloja `id_usuario` a NULL-able ahí mismo, y crea los índices
  `ix_auditoria_usuario_fecha` y `ux_usuarios_nombre_usuario_minusculas`). Mismo comando de
  siempre, cambiando el nombre de la base al final:
  ```bash
  source ./entorno.sh && PGPASSWORD=$(grep '^bd.contrasena=' \
    src/main/resources/configuracion/bd.properties | cut -d= -f2-) \
    psql -h localhost -U sgcpc_user -d <la_base> -v ON_ERROR_STOP=1 \
    -f bd/migraciones/E10_10_parametro_minutos_bloqueo.sql
  ```
  (y lo mismo con `E10_11_seguridad_auditoria.sql`). Ambas ya están incorporadas a
  `bd/01_esquema.sql`/`bd/02_indices.sql`/`bd/03_parametros.sql`, así que una base nueva creada
  con `bd/recrear_base.sh` nace directamente con estos cambios — las migraciones son solo para
  una base que ya existía antes de este cambio (como `sgcpc_db_pruebas`, donde ya se aplicaron).
- **`auditoria_operaciones` es de solo inserción**: dos triggers (`trg_bloquear_eliminacion`,
  `trg_bloquear_actualizacion`, en `bd/02_indices.sql`) rechazan cualquier `UPDATE`/`DELETE` sobre
  esa tabla, para cualquier usuario de la base (ningún permiso puede saltarlos). Esto ya estaba
  desde antes de este bloque de seguridad -- se verificó que sigue activo y que ningún código de
  la aplicación intenta modificar ni borrar auditoría.

## Recuperación de acceso

**Contexto (RF36/RF37):** por D-151, el módulo de Usuarios (alta, bloquear, inactivar,
reactivar, restablecer contraseña) es exclusivo del perfil ADMINISTRADOR — el gerente no tiene
acceso. De menor a mayor gravedad, así se resuelve quedarse sin acceso:

### 1. Un usuario cualquiera (gerente, oficial, cajera, u otro administrador) olvidó su contraseña

Lo resuelve **cualquier administrador activo**, desde Usuarios → "Restablecer contraseña": genera
una temporal nueva (con `SecureRandom`, sin caracteres ambiguos), obliga a cambiarla en el
próximo ingreso, y **si la cuenta estaba bloqueada, también la desbloquea** en el mismo paso (no
hace falta un segundo clic en "Reactivar"). Queda auditado (`RESTABLECER_CONTRASENA`, y
`REACTIVAR_USUARIO` si además se desbloqueó).

### 2. El segundo administrador

**Por eso conviene tener siempre al menos 2 administradores ACTIVOS** — el panel le avisa a
cualquier administrador, apenas entra, cuando quedan menos de 2 ("Recomendado: tener al menos 2
administradores activos para no quedar sin acceso."). Si uno se bloquea, se olvida la contraseña,
o hasta si pierde el perfil de administrador por error, el sistema ya lo impide (no se puede
bloquear, inactivar ni quitarle el perfil de administrador al último administrador ACTIVO —
mensaje claro: "Debe quedar al menos un administrador activo."); con 2 administradores, el que
queda resuelve todo desde Usuarios, sin salir de la aplicación.

### 3. Cuenta de emergencia en sobre cerrado

Recomendación operativa (procedimiento para Joel/el dueño, no algo que el código resuelva):
mantener una cuenta ADMINISTRADOR adicional, dedicada solo a emergencias (nunca de uso diario),
con sus credenciales guardadas en un sobre cerrado por el dueño del negocio. Se abre únicamente
si los administradores de uso diario quedan sin acceso a la vez (los 2 bloqueados, por ejemplo).
Dos cosas importantes:
- **Queda registrada igual que cualquier acceso** (`ACCESO_CONCEDIDO` con su IP y navegador) —
  usarla no es invisible para la auditoría.
- **Hay que cambiarle la contraseña enseguida después de usarla** (desde el menú del usuario,
  "Cambiar mi contraseña"), para que el sobre vuelva a guardar una credencial que nadie más usó
  mientras estuvo abierto.

### 4. Script de recuperación (último recurso, fuera de la aplicación)

Si ni el punto 2 ni el 3 alcanzan (ej. los dos administradores bloqueados a la vez y el sobre no
está a mano), el último recurso es actuar directo sobre la base, sin pasar por la aplicación:

- **Windows/Linux con Git Bash**: `bd/desbloquear_usuario.sh <usuario> [--restablecer --motivo "..."]`
- **Windows con CMD o PowerShell** (equivalente, mismo uso): `bd\desbloquear_usuario.bat <usuario> [/restablecer /motivo "..."]`
  — antes, correr `entorno.bat` en la misma ventana (para tener `psql`/`mvn` en el PATH, igual
  que para cualquier otro comando de este proyecto).

Reactiva la cuenta (ACTIVA, intentos en 0, debe cambiar contraseña al entrar) **sin tocar la
contraseña**. Con `--restablecer`/`--motivo` (o `/restablecer`/`/motivo` en el `.bat`) además
genera una contraseña temporal nueva (mismo mecanismo que el punto 1, pero corrido fuera de la
aplicación) y anota el motivo en `bd/desbloquear_usuario.log` (fecha, base, usuario, motivo —
nunca la contraseña).

**Advertencias (leer antes de correrlo):**

- Es un recurso de **emergencia**, no un procedimiento de rutina — primero los puntos 1 a 3.
- Se ejecuta **avisando antes**, contra la base correcta — nunca a ciegas.
- Reactivar (sin `--restablecer`) **no pasa por la aplicación**: no queda ningún registro en
  `auditoria_operaciones` (a diferencia del desbloqueo automático por tiempo, o de
  `--restablecer`, que sí generan un registro — el primero automático, el segundo porque llama al
  mismo mecanismo de la aplicación). Quien lo corre debe poder explicar después cuándo y por qué.
- Sin `--restablecer`, nunca cambia `contrasena_hash` — si además se perdió la contraseña, hace
  falta `--restablecer` (o resolverlo por SQL directo, con el hash generado aparte).

**Consulta SQL directa, si ni el script sirve** (por ejemplo, para solo reactivar sin generar
ninguna temporal, algo que el script ya hace mejor, o como diagnóstico):

```sql
-- Reemplazar <id_usuario> por el id real (SELECT id_usuario, nombre_usuario, estado_cuenta
-- FROM usuarios WHERE nombre_usuario = '...'; para encontrarlo). No cambia la contraseña.
UPDATE usuarios SET estado_cuenta = 'ACTIVA', intentos_fallidos = 0 WHERE id_usuario = <id_usuario>;
```
