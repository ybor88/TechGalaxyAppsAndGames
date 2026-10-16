@echo off
setlocal EnableExtensions EnableDelayedExpansion
title MatchMind - Build e avvio
REM MatchMind - test, build APK, emulatore e avvio app. (c) Roberto Di Flumeri
REM Uso:  start.bat                 test + build + emulatore + installa + avvia
REM       start.bat --solo-apk      test + build, genera MatchMind.apk senza emulatore
REM       start.bat --salta-test    come sopra ma senza unit test
REM       start.bat NomeAVD         usa un emulatore specifico
REM       start.bat --dry-run       mostra i comandi senza eseguirli

set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
REM Gradle lavora sulla cartella corrente: si parte sempre dalla cartella del progetto
cd /d "%PROJECT_DIR%"
set "APP_ID=com.matchmind.app"
set "MAIN_ACTIVITY=com.matchmind.app.MainActivity"
set "GRADLEW=%PROJECT_DIR%\gradlew.bat"
set "APK_PATH=%PROJECT_DIR%\app\build\outputs\apk\debug\app-debug.apk"
set "APK_FINALE=%PROJECT_DIR%\MatchMind.apk"
set "DRY_RUN=0"
set "SOLO_APK=0"
set "SALTA_TEST=0"
set "AVD_NAME="

:parseArgs
if "%~1"=="" goto argsDone
if /I "%~1"=="--dry-run" (
  set "DRY_RUN=1"
) else if /I "%~1"=="--solo-apk" (
  set "SOLO_APK=1"
) else if /I "%~1"=="--salta-test" (
  set "SALTA_TEST=1"
) else (
  set "AVD_NAME=%~1"
)
shift
goto parseArgs

:argsDone
echo.
echo  ==================================================
echo     MatchMind  -  pronostici sulle partite reali
echo  ==================================================
echo.

if not exist "%GRADLEW%" (
  echo [ERRORE] gradlew.bat non trovato in "%PROJECT_DIR%".
  goto fallito
)

REM -- JDK: quello di Android Studio se JAVA_HOME non e' impostato
if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

REM -- SDK Android da local.properties, ANDROID_SDK_ROOT o ANDROID_HOME
set "SDK_DIR="
if exist "%PROJECT_DIR%\local.properties" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%PROJECT_DIR%\local.properties") do (
    if /I "%%A"=="sdk.dir" set "SDK_DIR=%%B"
  )
)
if defined SDK_DIR (
  set "SDK_DIR=%SDK_DIR:\\=\%"
  set "SDK_DIR=%SDK_DIR:\:=:%"
)
if not defined SDK_DIR if defined ANDROID_SDK_ROOT set "SDK_DIR=%ANDROID_SDK_ROOT%"
if not defined SDK_DIR if defined ANDROID_HOME set "SDK_DIR=%ANDROID_HOME%"
if not defined SDK_DIR if exist "%LOCALAPPDATA%\Android\Sdk" set "SDK_DIR=%LOCALAPPDATA%\Android\Sdk"
if not defined SDK_DIR (
  echo [ERRORE] SDK Android non trovato. Configura sdk.dir in local.properties oppure ANDROID_SDK_ROOT.
  goto fallito
)
if not exist "%PROJECT_DIR%\local.properties" (
  >"%PROJECT_DIR%\local.properties" echo sdk.dir=%SDK_DIR:\=\\%
)

REM ==================================================================
echo [1/4] Unit test...
if "%SALTA_TEST%"=="1" (
  echo     saltati su richiesta.
) else if "%DRY_RUN%"=="1" (
  echo [DRY-RUN] "%GRADLEW%" testDebugUnitTest
) else (
  call "%GRADLEW%" testDebugUnitTest --console=plain -q
  if errorlevel 1 (
    echo [ERRORE] Unit test falliti o non eseguiti: vedi i messaggi sopra e app\build\reports\tests\testDebugUnitTest\index.html
    goto fallito
  )
  echo     Test superati.
)

REM ==================================================================
echo [2/4] Build APK...
if "%DRY_RUN%"=="1" (
  echo [DRY-RUN] "%GRADLEW%" assembleDebug
  echo [DRY-RUN] copy "%APK_PATH%" "%APK_FINALE%"
) else (
  call "%GRADLEW%" assembleDebug --console=plain -q
  if errorlevel 1 (
    echo [ERRORE] Build fallita.
    goto fallito
  )
  if not exist "%APK_PATH%" (
    echo [ERRORE] APK non trovato in "%APK_PATH%".
    goto fallito
  )
  copy /Y "%APK_PATH%" "%APK_FINALE%" >nul
  echo [OK] APK pronto: %APK_FINALE%
)

if "%SOLO_APK%"=="1" goto fine

REM ==================================================================
echo [3/4] Emulatore...
set "ADB_EXE=%SDK_DIR%\platform-tools\adb.exe"
set "EMULATOR_EXE=%SDK_DIR%\emulator\emulator.exe"
if not exist "%ADB_EXE%" (
  echo [AVVISO] adb.exe non trovato: installa l'APK a mano copiandolo sul telefono.
  goto fine
)

set "DEVICE_SERIAL="
REM il server adb va avviato prima di elencare i dispositivi, altrimenti la prima lista e' vuota
"%ADB_EXE%" start-server >nul 2>&1
for /f "skip=1 tokens=1,2" %%D in ('"%ADB_EXE%" devices') do (
  if "%%E"=="device" if not defined DEVICE_SERIAL set "DEVICE_SERIAL=%%D"
)

if not defined DEVICE_SERIAL (
  if not exist "%EMULATOR_EXE%" (
    echo [AVVISO] Nessun dispositivo collegato ed emulatore non installato: APK pronto in %APK_FINALE%
    goto fine
  )
  if not defined AVD_NAME (
    for /f "delims=" %%A in ('"%EMULATOR_EXE%" -list-avds') do (
      if not defined AVD_NAME set "AVD_NAME=%%A"
    )
  )
  if not defined AVD_NAME (
    echo [AVVISO] Nessun AVD disponibile ^(creane uno in Android Studio, Device Manager^). APK pronto in %APK_FINALE%
    goto fine
  )

  echo     Avvio emulatore: !AVD_NAME!
  if "%DRY_RUN%"=="1" (
    echo [DRY-RUN] "%EMULATOR_EXE%" -avd "!AVD_NAME!"
    set "DEVICE_SERIAL=emulator-5554"
  ) else (
    start "" "%EMULATOR_EXE%" -avd "!AVD_NAME!"
    set "EMU_PROCESS_OK="
    for /l %%I in (1,1,15) do (
      if not defined EMU_PROCESS_OK (
        tasklist /FI "IMAGENAME eq emulator.exe" | find /I "emulator.exe" >nul
        if not errorlevel 1 (set "EMU_PROCESS_OK=1") else (ping -n 2 127.0.0.1 >nul)
      )
    )
    if not defined EMU_PROCESS_OK (
      echo [ERRORE] Emulatore non avviato. Verifica AVD e virtualizzazione ^(WHPX/Hyper-V^).
      goto fallito
    )
    echo     Attendo che l'emulatore sia pronto...
    "%ADB_EXE%" wait-for-device >nul
    call :attendi_avvio "%ADB_EXE%"
    if errorlevel 1 goto fallito
    for /f "skip=1 tokens=1,2" %%D in ('"%ADB_EXE%" devices') do (
      if "%%E"=="device" if not defined DEVICE_SERIAL set "DEVICE_SERIAL=%%D"
    )
  )
)
if not defined DEVICE_SERIAL set "DEVICE_SERIAL=emulator-5554"
echo     Dispositivo: %DEVICE_SERIAL%

REM ==================================================================
echo [4/4] Installo e avvio MatchMind...
if "%DRY_RUN%"=="1" (
  echo [DRY-RUN] "%ADB_EXE%" -s "%DEVICE_SERIAL%" install -r "%APK_PATH%"
  echo [DRY-RUN] "%ADB_EXE%" -s "%DEVICE_SERIAL%" shell am start -n "%APP_ID%/%MAIN_ACTIVITY%"
  goto fine
)
"%ADB_EXE%" -s "%DEVICE_SERIAL%" install -r "%APK_PATH%"
if errorlevel 1 (
  echo [ERRORE] Installazione APK fallita.
  goto fallito
)
"%ADB_EXE%" -s "%DEVICE_SERIAL%" shell am start -n "%APP_ID%/%MAIN_ACTIVITY%" >nul
if errorlevel 1 (
  echo [ERRORE] Non riesco ad avviare %APP_ID%.
  goto fallito
)

:fine
echo.
echo  ==================================================
echo    Completato. APK: %APK_FINALE%
echo    Per il telefono: copia MatchMind.apk e aprilo
echo    (consenti "installa app sconosciute").
echo    (c) Roberto Di Flumeri
echo  ==================================================
echo.
if "%DRY_RUN%"=="0" pause
exit /b 0

:fallito
echo.
pause
exit /b 1

:attendi_avvio
set "BOOT_VALUE="
for /l %%I in (1,1,120) do (
  for /f "delims=" %%B in ('"%~1" shell getprop sys.boot_completed 2^>nul') do set "BOOT_VALUE=%%B"
  if "!BOOT_VALUE!"=="1" exit /b 0
  ping -n 3 127.0.0.1 >nul
)
echo [ERRORE] Timeout avvio emulatore (sys.boot_completed diverso da 1).
exit /b 1
