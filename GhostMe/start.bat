@echo off
setlocal enabledelayedexpansion
title Ghost Me - Avvio
REM Ghost Me - avvio completo: backend FastAPI (porta 8100) + frontend PWA Next.js (porta 3100)
REM (c) Roberto Di Flumeri

echo.
echo  ======================================================
echo           Ghost Me  -  una copia digitale di te
echo  ======================================================
echo.

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
set "FRONTEND=%ROOT%frontend"
set "PORTA_BACKEND=8100"
set "PORTA_FRONTEND=3100"

REM -- Node.js nel PATH se installato nel percorso standard
where npm >nul 2>&1
if errorlevel 1 (
    if exist "C:\Program Files\nodejs\npm.cmd" set "PATH=C:\Program Files\nodejs;%PATH%"
)
where npm >nul 2>&1
if errorlevel 1 (
    echo [ERRORE] Node.js non trovato. Installalo da https://nodejs.org ^(versione 20 o successiva^).
    pause & exit /b 1
)

REM -- Python: launcher py, poi installazioni standard per utente
set "PYTHON="
where py >nul 2>&1 && set "PYTHON=py"
if not defined PYTHON (
    if exist "%LOCALAPPDATA%\Programs\Python\Launcher\py.exe" set "PYTHON=%LOCALAPPDATA%\Programs\Python\Launcher\py.exe"
)
if not defined PYTHON (
    for /d %%i in ("%LOCALAPPDATA%\Programs\Python\Python3*") do (
        if exist "%%i\python.exe" set "PYTHON=%%i\python.exe"
    )
)
if not defined PYTHON (
    where python >nul 2>&1 && set "PYTHON=python"
)
if not defined PYTHON (
    echo [ERRORE] Python non trovato. Installalo da https://python.org ^(versione 3.11 o successiva^).
    pause & exit /b 1
)
echo     Python: %PYTHON%
echo.

REM ==========================================================================
echo [0/5] Chiusura di eventuali avvii precedenti di Ghost Me...
REM    solo i processi in ascolto sulle porte 8100 e 3100, nessun altro
for %%P in (%PORTA_BACKEND% %PORTA_FRONTEND%) do (
    for /f "tokens=2,5" %%a in ('netstat -ano 2^>nul ^| findstr /c:"LISTENING"') do (
        echo %%a| findstr /e /c:":%%P" >nul && taskkill /f /pid %%b >nul 2>&1
    )
)
echo     Fatto.
echo.

REM ==========================================================================
echo [1/5] Backend: ambiente Python e dipendenze...
cd /d "%BACKEND%"
if not exist ".venv\Scripts\python.exe" (
    echo     Creazione ambiente virtuale...
    "%PYTHON%" -m venv .venv
    if errorlevel 1 (
        echo [ERRORE] Creazione dell'ambiente Python non riuscita.
        pause & exit /b 1
    )
)
".venv\Scripts\python.exe" -m pip install -q --disable-pip-version-check -r requirements.txt
if errorlevel 1 (
    echo [ERRORE] Installazione delle dipendenze del backend non riuscita.
    pause & exit /b 1
)
echo     Backend pronto.
echo.

REM ==========================================================================
echo [2/5] Utenti dimostrativi...
REM    idempotenti: creano gli utenti solo se non esistono, i dati esistenti restano intatti
".venv\Scripts\python.exe" scripts\demo.py
".venv\Scripts\python.exe" scripts\popola.py 2>nul | findstr /v /c:" INFO "
echo.

REM ==========================================================================
echo [3/5] Frontend: dipendenze e build...
cd /d "%FRONTEND%"
if not exist "node_modules" (
    echo     Installazione dipendenze ^(solo la prima volta, qualche minuto^)...
    call npm install --no-audit --no-fund --loglevel=error
    if errorlevel 1 (
        echo [ERRORE] npm install non riuscito.
        pause & exit /b 1
    )
)
call npm run build >"%TEMP%\ghostme-build.log" 2>&1
if errorlevel 1 (
    echo [ERRORE] Build del frontend non riuscita. Dettagli:
    type "%TEMP%\ghostme-build.log"
    pause & exit /b 1
)
echo     Frontend pronto.
echo.

REM ==========================================================================
echo [4/5] Motore AI...
where ollama >nul 2>&1
if errorlevel 1 (
    if exist "%LOCALAPPDATA%\Programs\Ollama\ollama.exe" (
        echo     Ollama installato: se lo avvii, puoi sceglierlo in Privacy e dati.
    ) else (
        echo     Ollama non installato: verra' usato il motore integrato ^(istantaneo^).
    )
) else (
    echo     Ollama disponibile: selezionabile in Privacy e dati. Default: motore integrato.
)
echo.

REM ==========================================================================
echo [5/5] Avvio servizi...
start "Ghost Me - Backend (porta %PORTA_BACKEND%)" /min cmd /k "cd /d "%BACKEND%" && .venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port %PORTA_BACKEND%"

echo     Attendo il backend...
set /a ATTESA=0
:attesa_backend
ping -n 2 127.0.0.1 >nul
netstat -ano | findstr /c:":%PORTA_BACKEND% " | findstr /c:"LISTENING" >nul
if not errorlevel 1 goto backend_ok
set /a ATTESA+=1
if %ATTESA% lss 40 goto attesa_backend
echo [AVVISO] Il backend non risponde dopo 40 secondi: controlla la finestra "Ghost Me - Backend".
goto avvia_frontend
:backend_ok
echo     Backend attivo sulla porta %PORTA_BACKEND%.

:avvia_frontend
start "Ghost Me - Frontend (porta %PORTA_FRONTEND%)" /min cmd /k "cd /d "%FRONTEND%" && npm run start"

echo     Attendo il frontend...
set /a ATTESA=0
:attesa_frontend
ping -n 2 127.0.0.1 >nul
netstat -ano | findstr /c:":%PORTA_FRONTEND% " | findstr /c:"LISTENING" >nul
if not errorlevel 1 goto frontend_ok
set /a ATTESA+=1
if %ATTESA% lss 40 goto attesa_frontend
echo [AVVISO] Il frontend non risponde dopo 40 secondi: controlla la finestra "Ghost Me - Frontend".
goto apri
:frontend_ok
echo     Frontend attivo sulla porta %PORTA_FRONTEND%.

:apri
if not defined GHOSTME_NO_BROWSER start "" "http://localhost:%PORTA_FRONTEND%"

echo.
echo  ======================================================
echo    Ghost Me in esecuzione
echo.
echo    App:        http://localhost:%PORTA_FRONTEND%
echo    API docs:   http://localhost:%PORTA_BACKEND%/docs
echo    Utente demo:   demo@ghostme.it   /  DemoGhost2026
echo    Avatar pieno:  sofia@ghostme.it  /  SofiaGhost2026
echo    Secondo utente: luca@ghostme.it  /  LucaGhost2026
echo  ======================================================
echo.
echo  Per fermare l'app chiudi le finestre "Ghost Me - Backend" e "Ghost Me - Frontend".
echo.
pause
