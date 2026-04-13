@echo off
REM Build e avvia TaskCrafter Java Desktop App
cd /d %~dp0

set "JAVA_HOME_LOCAL=C:\Program Files\Java\jdk-26"
set "MAVEN_HOME_LOCAL=C:\apache-maven-3.9.14"
set "MVN_CMD=%MAVEN_HOME_LOCAL%\bin\mvn.cmd"
set "JAVA_CMD=java"

if not "%JAVA_HOME_LOCAL%"=="" (
    if /I "%JAVA_HOME_LOCAL:~-4%"=="\bin" (
        set "JAVA_HOME_LOCAL=%JAVA_HOME_LOCAL:~0,-4%"
    )
)

if not "%JAVA_HOME%"=="" (
    if not exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_HOME="
    )
)

if "%JAVA_HOME%"=="" (
    if not "%JAVA_HOME_LOCAL%"=="" (
        if exist "%JAVA_HOME_LOCAL%\bin\java.exe" (
            set "JAVA_HOME=%JAVA_HOME_LOCAL%"
        )
    )
)

if "%JAVA_HOME%"=="" (
    for /f "delims=" %%I in ('where java 2^>nul') do (
        set "JAVA_EXE=%%I"
        goto :JAVA_FROM_PATH_FOUND
    )
)

:JAVA_FROM_PATH_FOUND
if defined JAVA_EXE (
    for %%I in ("%JAVA_EXE%") do set "JAVA_HOME=%%~dpI.."
)

if "%JAVA_HOME%"=="" (
    echo Java non trovata o JAVA_HOME non valida.
    echo Imposta JAVA_HOME_LOCAL in questo file, ad esempio:
    echo set "JAVA_HOME_LOCAL=C:\Program Files\Java\jdk-26"
    pause
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JAVA_HOME non punta a un JDK valido: %JAVA_HOME%
    echo Correggi JAVA_HOME o JAVA_HOME_LOCAL.
    pause
    exit /b 1
)

set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"

if not exist "%MVN_CMD%" (
    set "MVN_CMD=mvn"
)

echo [1/2] Build in corso...
call "%MVN_CMD%" clean package
set BUILD_RESULT=%ERRORLEVEL%
if %BUILD_RESULT% neq 0 (
    echo Build fallita. Controlla gli errori sopra.
    echo Maven usato: %MVN_CMD%
    pause
    exit /b %BUILD_RESULT%
)

echo [2/2] Avvio applicazione...
"%JAVA_CMD%" -jar target\TaskCrafterApplication-1.0-SNAPSHOT-jar-with-dependencies.jar
pause
