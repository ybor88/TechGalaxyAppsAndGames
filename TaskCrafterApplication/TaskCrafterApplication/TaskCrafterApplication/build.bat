@echo off
REM Build TaskCrafter Java Desktop App
cd /d %~dp0
mvn clean package
pause