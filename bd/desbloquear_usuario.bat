@echo off
rem SGCPC - desbloquear_usuario.bat
rem Equivalente Windows de bd/desbloquear_usuario.sh (mismo procedimiento de recuperacion,
rem E2 punto 4 / RF36/RF37): reactiva a mano un usuario bloqueado (ACTIVA, intentos en 0,
rem debe_cambiar_contrasena=TRUE) cuando no queda ningun otro administrador activo para
rem hacerlo desde la pantalla de Usuarios.
rem
rem Por defecto NO cambia la contrasena en si (queda la que ya tenia). Con /restablecer
rem ademas genera una contrasena temporal nueva (mismo mecanismo que ServicioUsuarios.
rem restablecerContrasena / RestablecerContrasenaUsuario, llamando a Maven) -- para eso
rem hace falta /motivo "<texto>" (obligatorio, nunca vacio): este script corre FUERA de la
rem aplicacion, sin sesion ni usuario validado, asi que el motivo es lo unico que documenta
rem por que se uso. Queda anotado en bd\desbloquear_usuario.log (fecha, base, usuario,
rem motivo -- nunca la contrasena) ademas de imprimirse en pantalla, UNA sola vez.
rem
rem La contrasena de conexion a la base se lee de bd.properties (nunca escrita en este
rem script) y se pasa a psql via la variable de entorno PGPASSWORD.
rem
rem Uso, desde sistema\ (cmd.exe o PowerShell):
rem   bd\desbloquear_usuario.bat gerente
rem   bd\desbloquear_usuario.bat gerente /restablecer /motivo "Se olvido la contrasena, sin otro administrador activo."
rem
rem Nota: /restablecer solo acepta un motivo de una sola "palabra" entre comillas en cmd.exe
rem clasico (las comillas internas de %* se pierden) -- si el motivo necesita ser mas largo,
rem usar la version con psql a mano de mas abajo, o PowerShell:
rem   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -U sgcpc_user -d <base> `
rem       -v ON_ERROR_STOP=1 -c "UPDATE usuarios SET estado_cuenta='ACTIVA', intentos_fallidos=0, debe_cambiar_contrasena=TRUE WHERE nombre_usuario='<usuario>';"
rem (con $env:PGPASSWORD = "<la de bd.properties>" seteada antes, en la misma ventana).

setlocal enabledelayedexpansion

if "%~1"=="" (
    echo Uso: bd\desbloquear_usuario.bat ^<nombre_usuario^> [/restablecer /motivo "texto"]
    exit /b 1
)

set "NOMBRE_USUARIO=%~1"
set "RESTABLECER=0"
set "MOTIVO="
shift /1

:parse_args
if "%~1"=="" goto after_args
if /i "%~1"=="/restablecer" (
    set "RESTABLECER=1"
    shift /1
    goto parse_args
)
if /i "%~1"=="/motivo" (
    set "MOTIVO=%~2"
    shift /1
    shift /1
    goto parse_args
)
echo Opcion no reconocida: %~1
echo Uso: bd\desbloquear_usuario.bat ^<nombre_usuario^> [/restablecer /motivo "texto"]
exit /b 1

:after_args
if "%RESTABLECER%"=="1" if "%MOTIVO%"=="" (
    echo Con /restablecer, /motivo es obligatorio ^(no puede estar vacio^).
    exit /b 1
)

set "CARPETA=%~dp0"
set "PROPIEDADES=%CARPETA%..\src\main\resources\configuracion\bd.properties"

if not exist "%PROPIEDADES%" (
    echo No se encontro "%PROPIEDADES%".
    echo Copiar bd.properties.ejemplo a bd.properties y completar la contrasena.
    exit /b 1
)

set "BD_URL="
set "BD_USUARIO="
set "PGPASSWORD="
for /f "usebackq tokens=1,* delims==" %%A in ("%PROPIEDADES%") do (
    if "%%A"=="bd.url" set "BD_URL=%%B"
    if "%%A"=="bd.usuario" set "BD_USUARIO=%%B"
    if "%%A"=="bd.contrasena" set "PGPASSWORD=%%B"
)

if "%BD_URL%"=="" (
    echo No se pudo leer bd.url de "%PROPIEDADES%".
    exit /b 1
)

rem bd.url tiene la forma jdbc:postgresql://HOST:PUERTO/BASE -- se separa a mano, sin
rem depender de ninguna utilidad externa (findstr/sed de Windows no son iguales a los de
rem Git Bash).
set "RESTO=%BD_URL:jdbc:postgresql://=%"
for /f "tokens=1,2 delims=/" %%A in ("%RESTO%") do (
    set "HOST_PUERTO=%%A"
    set "BD_NOMBRE=%%B"
)
for /f "tokens=1,2 delims=:" %%A in ("%HOST_PUERTO%") do (
    set "BD_HOST=%%A"
    set "BD_PUERTO=%%B"
)

echo == Reactivando '%NOMBRE_USUARIO%' en '%BD_NOMBRE%' ==
psql -h "%BD_HOST%" -p "%BD_PUERTO%" -U "%BD_USUARIO%" -d "%BD_NOMBRE%" -v ON_ERROR_STOP=1 ^
    -c "UPDATE usuarios SET estado_cuenta = 'ACTIVA', intentos_fallidos = 0, debe_cambiar_contrasena = TRUE WHERE nombre_usuario = '%NOMBRE_USUARIO%';"
if errorlevel 1 (
    echo No se pudo reactivar -- revisar el error de psql arriba.
    exit /b 1
)
echo == '%NOMBRE_USUARIO%' queda ACTIVA y debe cambiar su contrasena al entrar ==

if "%RESTABLECER%"=="1" (
    echo == Generando una contrasena temporal nueva para '%NOMBRE_USUARIO%' ==
    pushd "%CARPETA%.."
    for /f "tokens=1,* delims==" %%A in ('mvn -q -Dtest=RestablecerContrasenaUsuario "-DnombreUsuario=%NOMBRE_USUARIO%" test 2^>^&1 ^| findstr "^CONTRASENA_TEMPORAL="') do set "CONTRASENA_TEMPORAL=%%B"
    popd
    if "%CONTRASENA_TEMPORAL%"=="" (
        echo No se pudo generar la contrasena temporal -- revise la salida de Maven arriba.
        exit /b 1
    )
    >>"%CARPETA%desbloquear_usuario.log" echo %date% %time% ^| base=%BD_NOMBRE% ^| usuario=%NOMBRE_USUARIO% ^| motivo=%MOTIVO%
    echo.
    echo == Contrasena temporal para '%NOMBRE_USUARIO%' ^(entreguesela ahora, no queda guardada en ningun lado^): %CONTRASENA_TEMPORAL% ==
    echo == Debe cambiarla en el proximo acceso. Motivo anotado en %CARPETA%desbloquear_usuario.log ==
)

endlocal
