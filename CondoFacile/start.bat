@echo off
title CondoFacile - Avvio
color 0C

echo.
echo  ============================================
echo   CondoFacile - Avvio applicazione
echo  ============================================
echo.

:: -- BUILD BACKEND --
echo [1/5] Build backend (NestJS)...
cd /d "%~dp0backend"
call npm run build
if errorlevel 1 (
    echo.
    echo  ERRORE: Build backend fallita.
    pause
    exit /b 1
)
echo  [OK] Backend compilato.
echo.

:: -- SEED UTENTI --
echo [2/5] Inizializzazione utenti di accesso...
cd /d "%~dp0backend"
node prisma/seed.js
echo  [OK] Utenti pronti.
echo.

:: -- BUILD FRONTEND --
echo [3/5] Build frontend (Next.js)...
cd /d "%~dp0frontend"
call npm run build
if errorlevel 1 (
    echo.
    echo  ERRORE: Build frontend fallita.
    pause
    exit /b 1
)
echo  [OK] Frontend compilato.
echo.

:: -- AVVIA BACKEND in nuova finestra --
echo [4/5] Avvio backend su http://localhost:3001 ...
start "CondoFacile - Backend (porta 3001)" cmd /k "cd /d %~dp0backend && npm run start:prod"

:: Attendi 3 secondi per dare tempo al backend di partire
timeout /t 3 /nobreak > nul

:: -- AVVIA FRONTEND in nuova finestra --
echo [5/5] Avvio frontend su http://localhost:3000 ...
start "CondoFacile - Frontend (porta 3000)" cmd /k "cd /d %~dp0frontend && npm run start"

:: Attendi che il frontend sia pronto, poi apri il browser
echo  Attendo avvio frontend...
timeout /t 6 /nobreak > nul
start "" "http://localhost:3000/login"

echo.
echo  ============================================
echo   App avviata!
echo   Frontend : http://localhost:3000
echo   Backend  : http://localhost:3001/api
echo   Login    : http://localhost:3000/login
echo  ============================================
echo.
echo  Puoi chiudere questa finestra.
echo  Le finestre Backend e Frontend devono restare aperte.
echo.
pause
