@echo off
rem Entorno del proyecto SGCPC para CMD. Solo afecta a esta ventana.
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
set "MAVEN_HOME=C:\Program Files\NetBeans-25\netbeans\java\maven"
set "PG_BIN=C:\Program Files\PostgreSQL\18\bin"
set "CATALINA_HOME=C:\tomcat9"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PG_BIN%;%PATH%"
set "PGCLIENTENCODING=UTF8"
if not exist "%JAVA_HOME%\bin\java.exe" echo AVISO: no se encuentra el JDK 17
if not exist "%MAVEN_HOME%\bin\mvn.cmd" echo AVISO: no se encuentra Maven de NetBeans
if not exist "%PG_BIN%\psql.exe" echo AVISO: no se encuentra psql 18
if not exist "%CATALINA_HOME%\bin\catalina.bat" echo AVISO: no se encuentra Tomcat 9 en C:\tomcat9
echo ===== Entorno SGCPC activo en esta ventana =====
java -version
call mvn -v
psql --version
