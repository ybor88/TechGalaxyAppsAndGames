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

:: ── BACKEND: virtualenv + dipendenze ─────────────────────────────────────
echo [1/4] Configurazione backend Python...
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
echo [2/4] Installazione dipendenze frontend...
cd /d "%FRONTEND%"

call npm install --silent
if errorlevel 1 (
    echo [ERRORE] npm install fallito. Verifica che Node.js sia installato.
    pause & exit /b 1
)

echo.
echo [3/4] Build frontend Next.js...
call npm run build
if errorlevel 1 (
    echo [ERRORE] Build frontend fallita.
    pause & exit /b 1
)
echo     Build completata.

:: ── AVVIO BACKEND in finestra separata ───────────────────────────────────
echo.
echo [4/4] Avvio servizi...
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
