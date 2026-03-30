@echo off
REM Build e avvia TaskCrafter Java Desktop App
cd /d %~dp0

echo [1/2] Build in corso...
call mvn clean package
set BUILD_RESULT=%ERRORLEVEL%
if %BUILD_RESULT% neq 0 (
    echo Build fallita. Controlla gli errori sopra.
    pause
    exit /b %BUILD_RESULT%
)

echo [2/2] Avvio applicazione...
java -jar target\TaskCrafterApplication-1.0-SNAPSHOT-jar-with-dependencies.jar
pause
