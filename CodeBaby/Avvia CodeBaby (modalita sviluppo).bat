@echo off
rem Avvia CodeBaby dal codice sorgente Python, invece che dall'eseguibile.
rem Utile solo se modifichi i file .py (per esempio levels.py) e vuoi
rem provare le modifiche senza dover ricreare CodeBaby.exe.
cd /d "%~dp0"
python -c "import pygame" >nul 2>nul
if errorlevel 1 (
    echo Installazione del componente grafico necessario, un attimo...
    python -m pip install --quiet pygame
)
start "" pythonw main.py
