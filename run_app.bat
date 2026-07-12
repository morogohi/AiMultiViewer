@echo off
REM ============================================================
REM  AI MultiViewer - launch emulator + install/run app
REM  (double-click to run)
REM ============================================================
setlocal
set "SDK=%USERPROFILE%\Android\Sdk"
if not exist "%SDK%" set "SDK=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%SDK%\platform-tools\adb.exe"
set "EMU=%SDK%\emulator\emulator.exe"
set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"

echo [1/4] Starting emulator window...
start "Android Emulator" "%EMU%" -avd testdev -gpu host -no-snapshot-load -no-audio

echo [2/4] Waiting for boot (up to 2 min)...
"%ADB%" wait-for-device
:waitboot
for /f %%i in ('"%ADB%" shell getprop sys.boot_completed 2^>nul') do set "BOOT=%%i"
if not "%BOOT%"=="1" (
    timeout /t 3 >nul
    goto waitboot
)

echo [3/4] Installing APK...
"%ADB%" install -r "%APK%"

echo [4/4] Launching app...
"%ADB%" shell am start -n com.aimultiviewer/.MainActivity

echo.
echo Done. Use the "AI MultiViewer" app in the emulator window.
echo (To re-upload test files, run upload_test_files.ps1)
pause
