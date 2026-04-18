@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "PROJECT_DIR=%~dp0"
set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"
set "APP_ID=com.example.frigozero"
set "MAIN_ACTIVITY=com.example.frigozero.MainActivity"
set "GRADLEW=%PROJECT_DIR%\gradlew.bat"
set "APK_PATH=%PROJECT_DIR%\app\build\outputs\apk\debug\app-debug.apk"
set "DRY_RUN=0"
set "AVD_NAME="

:parseArgs
if "%~1"=="" goto argsDone
if /I "%~1"=="--dry-run" (
  set "DRY_RUN=1"
) else (
  set "AVD_NAME=%~1"
)
shift
goto parseArgs

:argsDone
echo [start.bat] Build + Emulator + Run app

if not exist "%GRADLEW%" (
  echo [ERROR] gradlew.bat non trovato in "%PROJECT_DIR%".
  exit /b 1
)

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

if not defined SDK_DIR (
  echo [ERROR] SDK Android non trovato. Configura sdk.dir in local.properties oppure ANDROID_SDK_ROOT.
  exit /b 1
)

set "ADB_EXE=%SDK_DIR%\platform-tools\adb.exe"
set "EMULATOR_EXE=%SDK_DIR%\emulator\emulator.exe"

if not exist "%ADB_EXE%" (
  echo [ERROR] adb.exe non trovato in "%ADB_EXE%".
  exit /b 1
)

if not exist "%EMULATOR_EXE%" (
  echo [ERROR] emulator.exe non trovato in "%EMULATOR_EXE%".
  exit /b 1
)

for /f "skip=1 tokens=1,2" %%D in ('"%ADB_EXE%" devices') do (
  if "%%E"=="device" (
    echo %%D | findstr /B "emulator-" >nul
    if not errorlevel 1 if not defined DEVICE_SERIAL set "DEVICE_SERIAL=%%D"
  )
)

if not defined DEVICE_SERIAL (
  if not defined AVD_NAME (
    for /f "delims=" %%A in ('"%EMULATOR_EXE%" -list-avds') do (
      if not defined AVD_NAME set "AVD_NAME=%%A"
    )
  )

  if not defined AVD_NAME (
    echo [ERROR] Nessun AVD disponibile. Creane uno in Android Studio Device Manager.
    exit /b 1
  )

  echo [INFO] Avvio emulator: !AVD_NAME!
  if "%DRY_RUN%"=="1" (
    echo [DRY-RUN] "%EMULATOR_EXE%" -avd "!AVD_NAME!"
    set "DEVICE_SERIAL=emulator-5554"
  ) else (
    start "" "%EMULATOR_EXE%" -avd "!AVD_NAME!"
    if errorlevel 1 (
      echo [ERROR] Impossibile avviare l'emulatore.
      exit /b 1
    )

    set "EMU_PROCESS_OK="
    for /l %%I in (1,1,15) do (
      tasklist /FI "IMAGENAME eq emulator.exe" | find /I "emulator.exe" >nul
      if not errorlevel 1 (
        set "EMU_PROCESS_OK=1"
      )
      if not defined EMU_PROCESS_OK timeout /t 1 /nobreak >nul
    )

    if not defined EMU_PROCESS_OK (
      echo [ERROR] Processo emulator.exe non avviato. Verifica AVD e virtualizzazione ^(WHPX/Hyper-V^).
      exit /b 1
    )

    echo [INFO] Attendo che l'emulatore sia online...
    "%ADB_EXE%" wait-for-device >nul

    call :wait_for_boot "%ADB_EXE%"
    if errorlevel 1 (
      exit /b 1
    )

    for /f "skip=1 tokens=1,2" %%D in ('"%ADB_EXE%" devices') do (
      if "%%E"=="device" (
        echo %%D | findstr /B "emulator-" >nul
        if not errorlevel 1 if not defined DEVICE_SERIAL set "DEVICE_SERIAL=%%D"
      )
    )
  )
)

if not defined DEVICE_SERIAL set "DEVICE_SERIAL=emulator-5554"
echo [INFO] Device target: %DEVICE_SERIAL%

echo [INFO] Build + install debug...
if "%DRY_RUN%"=="1" (
  echo [DRY-RUN] "%GRADLEW%" installDebug
) else (
  call "%GRADLEW%" installDebug
  if errorlevel 1 (
    echo [WARN] installDebug fallito, provo assembleDebug + adb install.
    call "%GRADLEW%" assembleDebug
    if errorlevel 1 (
      echo [ERROR] Build fallita.
      exit /b 1
    )

    if not exist "%APK_PATH%" (
      echo [ERROR] APK debug non trovato in "%APK_PATH%".
      exit /b 1
    )

    "%ADB_EXE%" -s "%DEVICE_SERIAL%" install -r "%APK_PATH%"
    if errorlevel 1 (
      echo [ERROR] Install APK fallita.
      exit /b 1
    )
  )
)

echo [INFO] Lancio app...
if "%DRY_RUN%"=="1" (
  echo [DRY-RUN] "%ADB_EXE%" -s "%DEVICE_SERIAL%" shell monkey -p "%APP_ID%" -c android.intent.category.LAUNCHER 1
  echo [DRY-RUN] "%ADB_EXE%" -s "%DEVICE_SERIAL%" shell am start -n "%APP_ID%/%MAIN_ACTIVITY%"
) else (
  "%ADB_EXE%" -s "%DEVICE_SERIAL%" shell monkey -p "%APP_ID%" -c android.intent.category.LAUNCHER 1 >nul 2>nul
  if errorlevel 1 (
    "%ADB_EXE%" -s "%DEVICE_SERIAL%" shell am start -n "%APP_ID%/%MAIN_ACTIVITY%"
    if errorlevel 1 (
      echo [ERROR] Non riesco a lanciare l'app %APP_ID%.
      exit /b 1
    )
  )
)

echo [OK] Completato.
exit /b 0

:wait_for_boot
set "BOOT_VALUE="
for /l %%I in (1,1,120) do (
  for /f "delims=" %%B in ('"%~1" shell getprop sys.boot_completed 2^>nul') do set "BOOT_VALUE=%%B"
  if "!BOOT_VALUE!"=="1" exit /b 0
  timeout /t 2 /nobreak >nul
)
echo [ERROR] Timeout avvio emulator (sys.boot_completed != 1).
exit /b 1

