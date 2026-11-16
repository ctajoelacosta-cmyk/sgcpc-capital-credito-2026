@echo off
rem SGCPC - respaldo_diario.bat (RNF16: copia de seguridad diaria, guardada fuera del servidor)
rem
rem Respalda la base de datos en 2 formatos (custom .backup + SQL plano .sql, con fecha y hora
rem en el nombre), igual que bd/respaldar.sh, y copia los 2 archivos a una carpeta de destino
rem configurable (pendrive o carpeta de red) -- pensado para correr solo, todos los dias, desde
rem el Programador de tareas de Windows (ver herramientas/windows/RESPALDO_WINDOWS.md).
rem
rem REGLA DE SEGURIDAD: este script SOLO respalda "sgcpc_db" (la base real, para el Windows
rem Server) o "sgcpc_db_pruebas" (para probarlo, por ejemplo en esta laptop) -- cualquier otro
rem nombre, se aborta sin hacer nada. La base sale de bd.properties por defecto; se puede
rem indicar otra como parametro UNICAMENTE para probar (nunca para apuntar a otra base real).
rem
rem Uso, desde sistema\ (despues de "entorno.bat"):
rem   herramientas\windows\respaldo_diario.bat
rem   herramientas\windows\respaldo_diario.bat sgcpc_db_pruebas
rem
rem La contrasena de conexion se lee de bd.properties (nunca escrita en este script) y se pasa
rem a pg_dump via la variable de entorno PGPASSWORD, mismo patron que bd/respaldar.sh y
rem bd/desbloquear_usuario.bat.

setlocal enabledelayedexpansion

rem ========================= CONFIGURACION (editar aca) =========================
rem Carpeta de destino "fuera del servidor" -- la letra del pendrive, o una carpeta de red
rem (ej. \\servidor-archivos\respaldos_sgcpc). Si no existe en el momento de correr este
rem script (por ejemplo, el pendrive no esta conectado), se avisa claro y el respaldo local
rem se hace igual, sin fallar.
set "CARPETA_DESTINO=E:\respaldos_sgcpc"
rem ================================================================================

set "CARPETA=%~dp0"
set "PROPIEDADES=%CARPETA%..\..\src\main\resources\configuracion\bd.properties"

if not exist "%PROPIEDADES%" (
    echo No se encontro "%PROPIEDADES%".
    echo Copie bd.properties.ejemplo a bd.properties y complete la contrasena.
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
if "%PGPASSWORD%"=="" (
    echo bd.contrasena esta vacia en "%PROPIEDADES%".
    exit /b 1
)

rem bd.url tiene la forma jdbc:postgresql://HOST:PUERTO/BASE -- se separa a mano, mismo
rem metodo que bd/desbloquear_usuario.bat (sin depender de herramientas externas).
set "RESTO=%BD_URL:jdbc:postgresql://=%"
for /f "tokens=1,2 delims=/" %%A in ("%RESTO%") do (
    set "HOST_PUERTO=%%A"
    set "BD_NOMBRE_PROPIEDADES=%%B"
)
for /f "tokens=1,2 delims=:" %%A in ("%HOST_PUERTO%") do (
    set "BD_HOST=%%A"
    set "BD_PUERTO=%%B"
)

set "BASE_PEDIDA=%~1"
if "%BASE_PEDIDA%"=="" (
    set "BD_NOMBRE=%BD_NOMBRE_PROPIEDADES%"
) else (
    set "BD_NOMBRE=%BASE_PEDIDA%"
)

rem REGLA DE SEGURIDAD: nunca se respalda una base que no sea exactamente una de estas dos.
if /i not "%BD_NOMBRE%"=="sgcpc_db" if /i not "%BD_NOMBRE%"=="sgcpc_db_pruebas" (
    echo == ABORTADO: este script solo respalda "sgcpc_db" o "sgcpc_db_pruebas". ==
    echo    Recibido: "%BD_NOMBRE%".
    exit /b 1
)

rem Fecha y hora con Get-Date (evita los problemas de formato regional de %%date%%/%%time%%).
for /f %%I in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "MARCA=%%I"
if "%MARCA%"=="" (
    echo No se pudo obtener la fecha y hora actuales.
    exit /b 1
)

set "CARPETA_LOCAL=%USERPROFILE%\respaldos_sgcpc"
if not exist "%CARPETA_LOCAL%" mkdir "%CARPETA_LOCAL%"

set "ARCHIVO_CUSTOM=%CARPETA_LOCAL%\sgcpc_%MARCA%.backup"
set "ARCHIVO_SQL=%CARPETA_LOCAL%\sgcpc_%MARCA%.sql"

echo == Respaldando "%BD_NOMBRE%" (host %BD_HOST%:%BD_PUERTO%) en "%CARPETA_LOCAL%" ==
pg_dump -h "%BD_HOST%" -p "%BD_PUERTO%" -U "%BD_USUARIO%" --no-owner --no-privileges -F c -f "%ARCHIVO_CUSTOM%" "%BD_NOMBRE%"
if errorlevel 1 (
    echo No se pudo generar el respaldo .backup -- revise el error de pg_dump de arriba.
    exit /b 1
)
pg_dump -h "%BD_HOST%" -p "%BD_PUERTO%" -U "%BD_USUARIO%" --no-owner --no-privileges -f "%ARCHIVO_SQL%" "%BD_NOMBRE%"
if errorlevel 1 (
    echo No se pudo generar el respaldo .sql -- revise el error de pg_dump de arriba.
    exit /b 1
)
echo == Respaldo local listo: ==
echo    %ARCHIVO_CUSTOM%
echo    %ARCHIVO_SQL%

echo.
if exist "%CARPETA_DESTINO%" (
    copy /y "%ARCHIVO_CUSTOM%" "%CARPETA_DESTINO%\" >nul
    copy /y "%ARCHIVO_SQL%" "%CARPETA_DESTINO%\" >nul
    echo == Copiado tambien a "%CARPETA_DESTINO%" ==
) else (
    echo == AVISO: no se encontro la carpeta de destino "%CARPETA_DESTINO%". ==
    echo    Por ejemplo, el pendrive puede no estar conectado en este momento. El respaldo
    echo    local SI se hizo, en "%CARPETA_LOCAL%". Conecte el destino y copie los 2 archivos
    echo    a mano, o vuelva a correr este script cuando este disponible.
)

rem Retencion: borra SOLO los respaldos de SGCPC (por nombre de archivo) con mas de 30 dias,
rem y SOLO dentro de estas 2 carpetas -- nunca en ninguna otra, nunca otros archivos.
echo.
echo == Borrando respaldos de SGCPC con mas de 30 dias en "%CARPETA_LOCAL%" ==
forfiles /p "%CARPETA_LOCAL%" /m "sgcpc_*.backup" /d -30 /c "cmd /c del @path" 2>nul
forfiles /p "%CARPETA_LOCAL%" /m "sgcpc_*.sql" /d -30 /c "cmd /c del @path" 2>nul

if exist "%CARPETA_DESTINO%" (
    echo == Borrando respaldos de SGCPC con mas de 30 dias en "%CARPETA_DESTINO%" ==
    forfiles /p "%CARPETA_DESTINO%" /m "sgcpc_*.backup" /d -30 /c "cmd /c del @path" 2>nul
    forfiles /p "%CARPETA_DESTINO%" /m "sgcpc_*.sql" /d -30 /c "cmd /c del @path" 2>nul
)

echo.
echo == Respaldo diario terminado ==
endlocal
