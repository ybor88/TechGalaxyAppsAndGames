@echo off
setlocal enabledelayedexpansion
title BusinessFlow ERP — Avvio

echo.
echo  ╔══════════════════════════════════════════════════════╗
echo  ║         BusinessFlow ERP  —  Avvio completo         ║
echo  ╚══════════════════════════════════════════════════════╝
echo.

set ROOT=%~dp0
set BACKEND=%ROOT%backend
set FRONTEND=%ROOT%frontend
set DB_FILE=%ROOT%backend\contabilita20.db
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
echo.

:: ══════════════════════════════════════════════════════════════════════════
:: STEP 0  —  Pulizia: termina processi orfani che potrebbero bloccare il DB
:: ══════════════════════════════════════════════════════════════════════════
echo [0/5] Pulizia processi orfani (rilascio lock database)...

:: Termina eventuale uvicorn rimasto dalla sessione precedente (porta 8000)
for /f "tokens=5" %%p in ('netstat -ano 2^>nul ^| findstr ":8000 "') do (
    taskkill /f /pid %%p >nul 2>&1
)
:: Breve pausa perché SQLite possa rilasciare i lock prima di andare avanti
timeout /t 2 /nobreak >nul
echo     Processi orfani terminati.
echo.

:: ══════════════════════════════════════════════════════════════════════════
:: STEP 1  —  Configura SQLite: WAL mode + busy_timeout (pre-avvio backend)
:: ══════════════════════════════════════════════════════════════════════════
echo [1/5] Configurazione database SQLite...

if exist "%DB_FILE%" (
    "%PYTHON%" -c "import sqlite3; db=sqlite3.connect(r'%DB_FILE%'); db.execute('PRAGMA journal_mode=WAL'); db.execute('PRAGMA busy_timeout=30000'); db.execute('PRAGMA synchronous=NORMAL'); db.commit(); db.close(); print('    WAL mode, busy_timeout=30s, synchronous=NORMAL applicati.')"
    if errorlevel 1 (
        echo     [AVVISO] Impostazione PRAGMA fallita; verra' applicata al primo avvio del backend.
    )
) else (
    echo     Database non ancora presente; verra' creato e configurato dal backend.
)
echo.

:: ── OLLAMA (AI Assistant locale) ─────────────────────────────────────────
echo [2/5] Configurazione Ollama (AI Assistant)...

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
echo [3/5] Configurazione backend Python...
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

echo     Installazione/aggiornamento dipendenze backend...
pip install -r requirements.txt --quiet
if errorlevel 1 (
    echo [ERRORE] Installazione dipendenze backend fallita.
    pause & exit /b 1
)
echo     Backend pronto.

:: ── FRONTEND: npm install + build ────────────────────────────────────────
echo.
echo [4/5] Installazione dipendenze e build frontend Next.js...
cd /d "%FRONTEND%"

call npm install --silent
if errorlevel 1 (
    echo [ERRORE] npm install fallito. Verifica che Node.js sia installato.
    pause & exit /b 1
)

call npm run build
if errorlevel 1 (
    echo [ERRORE] Build frontend fallita.
    pause & exit /b 1
)
echo     Frontend pronto.

:: ── AVVIO SERVIZI ────────────────────────────────────────────────────────
echo.
echo [5/5] Avvio servizi...

:: Backend
start "BusinessFlow — Backend (porta 8000)" cmd /k "cd /d %BACKEND% && call .venv\Scripts\activate.bat && uvicorn app.main:app --host 127.0.0.1 --port 8000"

:: Attende che il backend sia in ascolto (max 40 s)
echo     Attendo avvio backend (porta 8000)...
set /a BACK_WAIT=0
:backend_wait_loop
timeout /t 2 /nobreak >nul
netstat -ano | findstr ":8000 " >nul 2>&1
if not errorlevel 1 goto :backend_ready
set /a BACK_WAIT+=2
if %BACK_WAIT% lss 40 goto :backend_wait_loop
echo     [AVVISO] Backend non risponde dopo 40s, proseguo comunque...
goto :backend_started

:backend_ready
echo     Backend attivo sulla porta 8000.

:backend_started

:: Frontend
start "BusinessFlow — Frontend (porta 3000)" cmd /k "cd /d %FRONTEND% && npm start"

:: Attende che il frontend sia in ascolto (max 40 s)
echo     Attendo avvio frontend (porta 3000)...
set /a FRONT_WAIT=0
:frontend_wait_loop
timeout /t 2 /nobreak >nul
netstat -ano | findstr ":3000 " >nul 2>&1
if not errorlevel 1 goto :frontend_ready
set /a FRONT_WAIT+=2
if %FRONT_WAIT% lss 40 goto :frontend_wait_loop
echo     [AVVISO] Frontend non risponde dopo 40s, proseguo comunque...
goto :frontend_started

:frontend_ready
echo     Frontend attivo sulla porta 3000.

:frontend_started

:: Apri browser
start http://localhost:3000

echo.
echo  ╔══════════════════════════════════════════════════════╗
echo  ║  BusinessFlow ERP in esecuzione                      ║
echo  ║                                                      ║
echo  ║  App:      http://localhost:3000                     ║
echo  ║  API Docs: http://localhost:8000/docs                ║
echo  ║  Ollama:   http://localhost:11434                    ║
echo  ╚══════════════════════════════════════════════════════╝
echo.
echo  Chiudi le finestre "Backend" e "Frontend" per fermare i servizi.
echo.
pause
