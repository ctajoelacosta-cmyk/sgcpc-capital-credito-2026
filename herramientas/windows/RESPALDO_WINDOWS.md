# Disponibilidad y respaldo en el Windows Server (RNF15/RNF16)

Procedimiento paso a paso para que Joel lo siga solo, en el Windows Server real de la
institución (Tomcat 9 + PostgreSQL 17). No toca nada del código del sistema — es
configuración del servidor y 2 scripts de esta misma carpeta (`respaldo_diario.bat`,
`restaurar_prueba.bat`).

## 1. RNF15 — que el sistema esté disponible todo el horario de atención

### 1.1 Dejar Tomcat 9 como servicio de Windows, con inicio automático

1. Abrir una ventana de **cmd.exe como Administrador**.
2. Ir a la carpeta `bin` de Tomcat (ej. `cd "C:\tomcat9\bin"`).
3. Instalar el servicio (si todavía no está instalado): `service.bat install Tomcat9`.
4. Abrir **Servicios** de Windows (`services.msc`).
5. Buscar "Apache Tomcat 9 Tomcat9" (o el nombre que haya quedado), clic derecho →
   Propiedades.
6. **Tipo de inicio: Automático**. Aplicar.

### 1.2 Confirmar que PostgreSQL 17 ya es un servicio con inicio automático

El instalador de PostgreSQL para Windows ya lo deja así por defecto — solo hay que
confirmarlo:

1. En **Servicios** (`services.msc`), buscar "postgresql-x64-17".
2. Confirmar **Tipo de inicio: Automático** y **Estado: En ejecución**. Si dice "Manual",
   cambiarlo a Automático igual que en el paso 1.1.

### 1.3 Que Tomcat espere a PostgreSQL al reiniciar

Para que Windows arranque PostgreSQL antes que Tomcat (y la aplicación no falle al conectar
los primeros segundos después de un reinicio), en cmd.exe como Administrador:

```
sc config Tomcat9 depend= postgresql-x64-17
```

### 1.4 Cómo comprobar que están bien configurados (sin reiniciar nada)

En PowerShell:

```powershell
Get-Service Tomcat9, postgresql-x64-17 | Select-Object Name, Status, StartType
```

Las dos filas deben decir `Status: Running` y `StartType: Automatic`.

### 1.5 La prueba real: reiniciar el servidor

Única forma de confirmar RNF15 de verdad — hacerla **fuera del horario de atención**:

1. Reiniciar el servidor (`Restart-Computer` en PowerShell, o desde el menú de Windows).
2. Esperar 2-3 minutos.
3. Sin tocar nada manualmente, abrir un navegador en otra PC de la red y entrar a
   `http://<IP-del-servidor>:8081/sgcpc/acceso`.
4. Si la pantalla de ingreso aparece sola → RNF15 cumple. Si no, revisar en `services.msc`
   cuál de los dos servicios no arrancó, y por qué (ver el registro de Visor de eventos de
   Windows → Registros de Windows → Sistema).

**Estado al 13/10/2026: procedimiento listo, prueba de reinicio todavía pendiente de hacerse
en el servidor real** (ver `RNF_revision.md`).

## 2. RNF16 — copia de seguridad diaria, guardada fuera del servidor

### 2.1 Qué hacen los 2 scripts de esta carpeta

- **`respaldo_diario.bat`**: respalda la base (`pg_dump`, 2 formatos: `.backup` y `.sql`, con
  fecha y hora en el nombre) en una carpeta local (`%USERPROFILE%\respaldos_sgcpc`) y, si
  encuentra la carpeta de destino configurada al principio del archivo (una unidad externa o
  de red), copia los 2 archivos ahí también. Al final, borra — SOLO dentro de esas 2
  carpetas, y SOLO archivos que empiecen con `sgcpc_` — los respaldos de más de 30 días.
- **`restaurar_prueba.bat`**: toma un `.backup` (o `.sql`) y lo restaura en la base
  descartable `sgcpc_db_restaurada`, compara el conteo de filas de cada tabla contra la base
  real, y la vuelve a vaciar al terminar. Nunca toca `sgcpc_db` salvo para leer (comparar).

### 2.2 Configurar `respaldo_diario.bat` en el servidor

Antes de usarlo en el servidor real, abrir `respaldo_diario.bat` con el Bloc de notas y
cambiar la línea:

```
set "CARPETA_DESTINO=E:\respaldos_sgcpc"
```

por la letra de la unidad externa que vaya a quedar conectada al servidor, o por una ruta de
red (ej. `\\servidor-archivos\respaldos_sgcpc`). Sin parámetros, el script respalda la base
que diga `bd.properties` en ese momento (en el servidor real, debe ser `sgcpc_db`).

### 2.3 Tarea programada diaria, a las 22:00

En cmd.exe como Administrador, parado en la carpeta `sistema\`:

```
schtasks /create /tn "SGCPC - Respaldo diario" /tr "cmd.exe /c \"cd C:\ruta\a\sistema && entorno.bat && herramientas\windows\respaldo_diario.bat\"" /sc daily /st 22:00 /ru "SYSTEM"
```

(Ajustar `C:\ruta\a\sistema` a la carpeta real del proyecto en el servidor.) Esto corre el
respaldo solo, todos los días a las 22:00 — cerca de la hora de cierre, sin depender de que
alguien se acuerde de hacer clic.

Para confirmarlo: **Programador de tareas** de Windows → buscar "SGCPC - Respaldo diario" →
botón derecho → **Ejecutar** (prueba manual inmediata) → revisar que aparezcan los 2 archivos
nuevos en la carpeta local y en la de destino.

### 2.4 Retención

30 días de respaldos diarios, acumulados tanto en la carpeta local como en la de destino —
ya lo hace solo el script (punto 2.1). No hace falta ninguna tarea adicional para esto.

### 2.5 Restauración mensual (prueba de que el respaldo sirve de verdad)

Una vez al mes, en cmd.exe (después de `entorno.bat`), desde `sistema\`:

```
herramientas\windows\restaurar_prueba.bat C:\ruta\al\respaldo\sgcpc_AAAAMMDD_HHMMSS.backup
```

(usar el `.backup` más reciente de `%USERPROFILE%\respaldos_sgcpc` o de la carpeta de
destino). El script muestra, tabla por tabla, "OK" o "FALLO" comparando contra la base real —
si todo dice OK, el respaldo de ese día sirve. Al final, la base de prueba queda vacía sola,
lista para el mes que viene. **Nunca correr este script apuntando a `sgcpc_db` ni a
`sgcpc_db_pruebas`** — el script mismo lo rechaza si se intenta.

**La base `sgcpc_db_restaurada` se crea una sola vez, a mano**, con el mismo dueño
(`sgcpc_user`) y codificación (UTF8) que `sgcpc_db` — pedirle ayuda a quien tenga acceso de
administrador de PostgreSQL en el servidor si todavía no existe.

**Estado al 13/10/2026: procedimiento probado en la laptop de Joel contra `sgcpc_db_pruebas`
(ver `RNF_revision.md`); pendiente repetirlo una vez en el Windows Server real contra
`sgcpc_db`.**
