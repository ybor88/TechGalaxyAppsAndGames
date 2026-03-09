@echo off
REM Avvia TaskCrafter GUI
cd /d %~dp0
java -jar target/TaskCrafterApplication-1.0-SNAPSHOT-jar-with-dependencies.jar
pause