@echo off
title CondoFacile - Dev Mode
color 0A

echo.
echo  ============================================
echo   CondoFacile - Modalita sviluppo (dev)
echo  ============================================
echo.

echo  Avvio backend su http://localhost:3001 ...
start "CondoFacile - Backend DEV" cmd /k "cd /d %~dp0backend && npm run start:dev"

timeout /t 3 /nobreak > nul

echo  Avvio frontend su http://localhost:3000 ...
start "CondoFacile - Frontend DEV" cmd /k "cd /d %~dp0frontend && npm run dev"

echo  Attendo avvio frontend...
timeout /t 8 /nobreak > nul
start "" "http://localhost:3000/dashboard"

echo   Frontend : http://localhost:3000
echo   Backend  : http://localhost:3001/api
echo   Hot-reload attivo su entrambi.
echo  ============================================
echo.
pause
