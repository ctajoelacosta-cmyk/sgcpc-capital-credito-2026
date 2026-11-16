@echo off
rem SGCPC - restaurar_prueba.bat (RNF16: prueba de restauracion, mensual)
rem
rem Restaura un respaldo (.backup o .sql, generado por respaldo_diario.bat o bd/respaldar.sh)
rem en la base DESECHABLE "sgcpc_db_restaurada" -- nunca en una base real. Compara el conteo de
rem filas de cada tabla contra la base que tenga bd.properties en ese momento (solo lectura,
rem nunca se modifica) y, al final, vacia "sgcpc_db_restaurada" para dejarla lista para la
rem proxima vez. Mismo patron que bd/restaurar_prueba.sh + bd/vaciar_prueba.sh.
rem
rem La base de prueba "sgcpc_db_restaurada" la crea Joel una sola vez a mano (mismo dueno
rem sgcpc_user, UTF8, igual que las demas -- sgcpc_user no puede crear ni borrar bases). Este
rem script solo le vacia y le vuelve a llenar el contenido.
rem
rem REGLA DE SEGURIDAD: el destino de la restauracion NUNCA puede ser "sgcpc_db" ni
rem "sgcpc_db_pruebas" -- si se pide cualquiera de las dos, se aborta sin tocar nada.
rem
rem Uso, desde sistema\ (despues de "entorno.bat"):
rem   herramientas\windows\restaurar_prueba.bat C:\Users\...\respaldos_sgcpc\sgcpc_20261014_220000.backup

setlocal enabledelayedexpansion

if "%~1"=="" (
    echo Uso: herramientas\windows\restaurar_prueba.bat ^<archivo .backup o .sql^> [base_prueba]
    exit /b 1
)
if not exist "%~1" (
    echo No se encontro el archivo "%~1".
    exit /b 1
)

set "ARCHIVO_BACKUP=%~1"
set "BASE_PRUEBA=%~2"
if "%BASE_PRUEBA%"=="" set "BASE_PRUEBA=sgcpc_db_restaurada"

if /i "%BASE_PRUEBA%"=="sgcpc_db" (
    echo == ABORTADO: nunca se restaura sobre "sgcpc_db" con este script. ==
    exit /b 1
)
if /i "%BASE_PRUEBA%"=="sgcpc_db_pruebas" (
    echo == ABORTADO: nunca se restaura sobre "sgcpc_db_pruebas" con este script. ==
    exit /b 1
)

set "CARPETA=%~dp0"
set "PROPIEDADES=%CARPETA%..\..\src\main\resources\configuracion\bd.properties"

if not exist "%PROPIEDADES%" (
    echo No se encontro "%PROPIEDADES%".
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

set "RESTO=%BD_URL:jdbc:postgresql://=%"
for /f "tokens=1,2 delims=/" %%A in ("%RESTO%") do (
    set "HOST_PUERTO=%%A"
    set "BD_NOMBRE_REAL=%%B"
)
for /f "tokens=1,2 delims=:" %%A in ("%HOST_PUERTO%") do (
    set "BD_HOST=%%A"
    set "BD_PUERTO=%%B"
)

echo == Vaciando "%BASE_PRUEBA%" (host %BD_HOST%:%BD_PUERTO%) ==
psql -h "%BD_HOST%" -p "%BD_PUERTO%" -U "%BD_USUARIO%" -d "%BASE_PRUEBA%" -v ON_ERROR_STOP=1 -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
if errorlevel 1 (
    echo No se pudo vaciar "%BASE_PRUEBA%" -- revise el error de psql de arriba.
    exit /b 1
)

echo == Restaurando "%ARCHIVO_BACKUP%" en "%BASE_PRUEBA%" ==
set "EXTENSION=%~x1"
if /i "%EXTENSION%"==".backup" (
    pg_restore -h "%BD_HOST%" -p "%BD_PUERTO%" -U "%BD_USUARIO%" -d "%BASE_PRUEBA%" --no-owner --role="%BD_USUARIO%" "%ARCHIVO_BACKUP%"
) else if /i "%EXTENSION%"==".sql" (
    psql -h "%BD_HOST%" -p "%BD_PUERTO%" -U "%BD_USUARIO%" -d "%BASE_PRUEBA%" -v ON_ERROR_STOP=1 -f "%ARCHIVO_BACKUP%"
) else (
    echo Extension no reconocida ^(se espera .backup o .sql^): "%ARCHIVO_BACKUP%"
    exit /b 1
)
if errorlevel 1 (
    echo No se pudo restaurar -- revise el error de arriba.
    exit /b 1
)

echo.
echo == Comparando el conteo de filas contra "%BD_NOMBRE_REAL%" (solo lectura, sin modificarla) ==
set "FALLOS=0"
for /f "usebackq delims=" %%T in (`psql -h "%BD_HOST%" -p "%BD_PUERTO%" -U "%BD_USUARIO%" -d "%BD_NOMBRE_REAL%" -tAc "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename"`) do (
    set "TABLA=%%T"
    if not "!TABLA!"=="" (
        for /f "usebackq delims=" %%R in (`psql -h "%BD_HOST%" -p "%BD_PUERTO%" -U "%BD_USUARIO%" -d "%BD_NOMBRE_REAL%" -tAc "SELECT count(*) FROM !TABLA!"`) do set "CONTEO_REAL=%%R"
        for /f "usebackq delims=" %%P in (`psql -h "%BD_HOST%" -p "%BD_PUERTO%" -U "%BD_USUARIO%" -d "%BASE_PRUEBA%" -tAc "SELECT count(*) FROM !TABLA!"`) do set "CONTEO_PRUEBA=%%P"
        if "!CONTEO_REAL!"=="!CONTEO_PRUEBA!" (
            echo   OK    !TABLA!: !CONTEO_PRUEBA! filas
        ) else (
            echo   FALLO !TABLA!: real=!CONTEO_REAL! prueba=!CONTEO_PRUEBA!
            set /a FALLOS+=1
        )
    )
)

echo.
if "%FALLOS%"=="0" (
    echo == RESTAURACION OK: "%BASE_PRUEBA%" coincide con "%BD_NOMBRE_REAL%" en el conteo de filas de todas las tablas ==
) else (
    echo == RESTAURACION CON %FALLOS% TABLA^(S^) CON DIFERENCIA: revise arriba ==
)

echo.
echo == Vaciando "%BASE_PRUEBA%" de nuevo, para dejarla lista para la proxima vez ==
psql -h "%BD_HOST%" -p "%BD_PUERTO%" -U "%BD_USUARIO%" -d "%BASE_PRUEBA%" -v ON_ERROR_STOP=1 -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
if errorlevel 1 (
    echo No se pudo vaciar "%BASE_PRUEBA%" al final -- revise el error de arriba.
    exit /b 1
)
echo == Listo: "%BASE_PRUEBA%" queda vacia ==

endlocal
