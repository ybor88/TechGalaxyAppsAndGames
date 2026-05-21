@echo off
setlocal enabledelayedexpansion
title BusinessFlow ERP — Avvio

echo.
echo  ╔══════════════════════════════════════╗
echo  ║        BusinessFlow ERP              ║
echo  ║        Avvio applicazione            ║
echo  ╚══════════════════════════════════════╝
echo.

set ROOT=%~dp0
set BACKEND=%ROOT%backend
set FRONTEND=%ROOT%frontend
set OLLAMA_MODEL=llama3

:: ── Aggiunge Node.js al PATH se non trovato ───────────────────────────────
where.exe npm >nul 2>&1
if errorlevel 1 (
    if exist "C:\Program Files\nodejs\npm.cmd" (
        set PATH=C:\Program Files\nodejs;%PATH%
    )
)

:: ── Rileva Python ─────────────────────────────────────────────────────────
set PYTHON=
where.exe py >nul 2>&1 && set PYTHON=py
if not defined PYTHON (
    if exist "%LOCALAPPDATA%\Programs\Python\Launcher\py.exe" (
        set PYTHON=%LOCALAPPDATA%\Programs\Python\Launcher\py.exe
    )
)
if not defined PYTHON (
    for /d %%i in ("%LOCALAPPDATA%\Programs\Python\Python3*") do (
        if exist "%%i\python.exe" set PYTHON=%%i\python.exe
    )
)
if not defined PYTHON (
    echo [ERRORE] Python non trovato. Installa Python 3.12+ da https://python.org
    pause & exit /b 1
)
echo     Python trovato: %PYTHON%

:: ── OLLAMA (AI Assistant locale) ─────────────────────────────────────────
echo [1/5] Configurazione Ollama (AI Assistant)...

:: Cerca ollama.exe: PATH, percorso standard standalone Windows
set OLLAMA_EXE=
where.exe ollama >nul 2>&1
if not errorlevel 1 (
    for /f "delims=" %%i in ('where.exe ollama') do set OLLAMA_EXE=%%i
)
if not defined OLLAMA_EXE (
    if exist "%LOCALAPPDATA%\Programs\Ollama\ollama.exe" (
        set OLLAMA_EXE=%LOCALAPPDATA%\Programs\Ollama\ollama.exe
    )
)
if not defined OLLAMA_EXE (
    if exist "%USERPROFILE%\AppData\Local\Programs\Ollama\ollama.exe" (
        set OLLAMA_EXE=%USERPROFILE%\AppData\Local\Programs\Ollama\ollama.exe
    )
)

if not defined OLLAMA_EXE (
    echo     [AVVISO] Ollama non trovato. Scaricalo da https://ollama.com/download
    echo     L'AI Assistant non sara' disponibile. Il resto dell'app funziona normalmente.
    goto :ollama_end
)

echo     Ollama trovato: %OLLAMA_EXE%

:: Controlla se Ollama e' gia' in ascolto sulla porta 11434
netstat -ano | findstr ":11434" >nul 2>&1
if not errorlevel 1 (
    echo     Ollama gia' in esecuzione sulla porta 11434.
    goto :ollama_check_model
)

:: Avvia ollama serve in finestra minimizzata e aspetta che sia pronto
echo     Avvio Ollama server...
start "Ollama Server" /min "%OLLAMA_EXE%" serve

:: Attende fino a 30 secondi che Ollama risponda
set /a OLLAMA_WAIT=0
:ollama_wait_loop
timeout /t 2 /nobreak >nul
netstat -ano | findstr ":11434" >nul 2>&1
if not errorlevel 1 goto :ollama_ready
set /a OLLAMA_WAIT+=2
if %OLLAMA_WAIT% lss 30 goto :ollama_wait_loop
echo     [AVVISO] Ollama non ha risposto in 30s. Proseguo comunque...
goto :ollama_end

:ollama_ready
echo     Ollama server pronto.

:ollama_check_model
:: Verifica se il modello e' gia' presente
"%OLLAMA_EXE%" list 2>nul | findstr /i "%OLLAMA_MODEL%" >nul 2>&1
if not errorlevel 1 (
    echo     Modello %OLLAMA_MODEL% gia' presente.
    goto :ollama_end
)

:: Pull sincrono del modello (avviene prima dell'avvio dell'app)
echo     Download modello %OLLAMA_MODEL% (prima esecuzione, attendere)...
echo     Dimensione ~4.7 GB. Potrebbe richiedere alcuni minuti.
"%OLLAMA_EXE%" pull %OLLAMA_MODEL%
if errorlevel 1 (
    echo     [AVVISO] Download modello fallito. Riprova dopo l'avvio.
) else (
    echo     Modello %OLLAMA_MODEL% scaricato con successo.
)

:ollama_end

:: ── BACKEND: virtualenv + dipendenze ─────────────────────────────────────
echo.
echo [2/5] Configurazione backend Python...
cd /d "%BACKEND%"

if not exist ".venv\Scripts\activate.bat" (
    echo     Creazione virtual environment...
    "%PYTHON%" -m venv .venv
    if errorlevel 1 (
        echo [ERRORE] Creazione virtual environment fallita.
        pause & exit /b 1
    )
)

call .venv\Scripts\activate.bat

echo     Installazione dipendenze backend...
pip install -r requirements.txt --quiet
if errorlevel 1 (
    echo [ERRORE] Installazione dipendenze backend fallita.
    pause & exit /b 1
)
echo     Backend pronto.

:: ── FRONTEND: npm install + build ────────────────────────────────────────
echo.
echo [3/5] Installazione dipendenze frontend...
cd /d "%FRONTEND%"

call npm install --silent
if errorlevel 1 (
    echo [ERRORE] npm install fallito. Verifica che Node.js sia installato.
    pause & exit /b 1
)

echo.
echo [4/5] Build frontend Next.js...
call npm run build
if errorlevel 1 (
    echo [ERRORE] Build frontend fallita.
    pause & exit /b 1
)
echo     Build completata.

:: ── AVVIO BACKEND in finestra separata ───────────────────────────────────
echo.
echo [5/5] Avvio servizi...
echo     Il database SQLite verra' creato automaticamente al primo avvio.

start "BusinessFlow — Backend (porta 8000)" cmd /k "cd /d %BACKEND% && call .venv\Scripts\activate.bat && uvicorn app.main:app --host 127.0.0.1 --port 8000"

:: attendi che il backend sia su
echo     Attendo avvio backend...
timeout /t 5 /nobreak >nul

:: ── AVVIO FRONTEND in finestra separata ──────────────────────────────────
start "BusinessFlow — Frontend (porta 3000)" cmd /k "cd /d %FRONTEND% && npm start"

:: attendi che Next.js sia pronto
timeout /t 4 /nobreak >nul

:: ── APRI BROWSER ─────────────────────────────────────────────────────────
start http://localhost:3000

echo.
echo  ✓ BusinessFlow ERP in esecuzione.
echo    Apri il browser su: http://localhost:3000
echo    API Docs           : http://localhost:8000/docs
echo.
echo  Chiudi le finestre "Backend" e "Frontend" per fermare i servizi.
echo.
pause
